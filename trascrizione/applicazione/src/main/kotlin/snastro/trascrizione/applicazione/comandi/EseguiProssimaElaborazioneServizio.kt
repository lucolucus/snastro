package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.Creato
import snastro.kernel.DispatcherEventi
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.FaseElaborazione.ALLINEAMENTO
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DECODIFICA
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.FaseElaborazione.TRASCRIZIONE
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.TrascrittoCreato
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Use-case `EseguiProssimaElaborazione` (AC-68..75, ADR 0004/0012): takes the oldest `in_attesa`
 * Elaborazione (FIFO), read and marked `in_corso` in the SAME short transaction (no read-then-avvia
 * race), then runs decodifica → diarizzazione → trascrizione → allineamento through the pinned
 * ML/audio ports of [PortePipeline] OUTSIDE any transaction (AC-73), signalling each phase in order
 * (AC-69) with one FIXED, plain-Italian `motivo` per fault point — no raw exception text, path or id
 * ever reaches the user (ADR 0003). On success, `completata` and the [Trascritto] are saved together
 * in one short transaction (INV-5): that transaction re-reads the Elaborazione BY ID first and, if it
 * is no longer `in_corso`, leaves it untouched (e.g. [RecuperaElaborazioniInterrotte] already
 * recovered it while this run was mid-flight — the in-memory instance mutated earlier is never
 * reused). If that final transaction itself fails — `Esito.Errore`, or an exception (the store refuses
 * the `completata` row after the Trascritto was already written in the SAME transaction: the whole
 * transaction rolls back, INV-5) — a COMPENSATING transaction re-reads the Elaborazione again and
 * marks it `fallita`; the fault never escapes uncaught (ADR 0003). If the compensation itself fails
 * too, the Elaborazione is left `in_corso`: [RecuperaElaborazioniInterrotte] (startup) recovers it as
 * `fallita('interrotta')`. [PortePipeline.segnalatore]`.terminata` always fires, in a `finally`,
 * whatever the outcome. Never decodes the whole Registrazione as an interval:
 * [PortePipeline.decodificatore]`.tutti` reads the whole audio, `.campioni(intervallo)` is for
 * turn/Segmento-scoped work elsewhere (review finding).
 */
public class EseguiProssimaElaborazioneServizio(
    private val uow: UnitaDiLavoro,
    private val orologio: Clock,
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: TrascrittoRepository,
    private val pipeline: PortePipeline,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(ignored: EseguiProssimaElaborazione): Esito<Unit> =
        uow.inTransazione { avviaLaPiuVecchia() }.poi { elaborazione ->
            elaborazione?.let { eseguiSu(it) } ?: Esito.Ok(Unit) // AC-68: nessuna in_attesa
        }

    /** Reads the oldest `in_attesa` and marks it `in_corso` in ONE transaction (no read/avvia race). */
    private fun avviaLaPiuVecchia(): Esito<Elaborazione?> {
        val prossima = elaborazioni.inAttesa().firstOrNull() ?: return Esito.Ok(null) // AC-68: FIFO
        return prossima.avvia(orologio.instant()).poi { evento ->
            elaborazioni.salva(prossima).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }.mappa { prossima }
    }

    private fun eseguiSu(elaborazione: Elaborazione): Esito<Unit> {
        val registrazioneId = elaborazione.registrazioneId
        val elaborazioneId = elaborazione.id
        return try {
            val risultato = eseguiPipeline(registrazioneId)
            val commesso = concludi(registrazioneId, elaborazioneId) { fresca ->
                when (risultato) {
                    is RisultatoPipeline.Successo ->
                        concludiConSuccesso(fresca, risultato.durataMs, risultato.segmenti)
                    is RisultatoPipeline.Fallita -> concludiConFallimento(fresca, risultato.motivo)
                }
            }
            // F2: la transazione finale e' fallita (Errore o eccezione) -> compensazione a fallita, mai
            // un'eccezione che sfugge non gestita. Se anche la compensazione fallisce, l'Elaborazione
            // resta `in_corso`: RecuperaElaborazioniInterrotte (avvio) la recupera come 'interrotta'.
            if (!commesso) {
                concludi(registrazioneId, elaborazioneId) { concludiConFallimento(it, MOTIVO_SALVATAGGIO_FALLITO) }
            }
            Esito.Ok(Unit)
        } finally {
            pipeline.segnalatore.terminata(registrazioneId) // sempre segnalata, qualunque sia l'esito
        }
    }

    /**
     * Runs the pipeline (never inside a transaction, AC-73) up to a [RisultatoPipeline]. One guard
     * clause per phase (readable, each mapped to its own fixed `motivo`) — `@Suppress`: deliberate.
     */
    @Suppress("ReturnCount")
    private fun eseguiPipeline(id: RegistrazioneId): RisultatoPipeline {
        val vista = pipeline.registrazioni.registrazione(id) // lookup miss atteso: niente error()
            ?: return RisultatoPipeline.Fallita(MOTIVO_REGISTRAZIONE_MANCANTE)

        pipeline.segnalatore.fase(id, DECODIFICA)
        val campioni = eseguiFase {
            pipeline.decodificatore.decodifica(id, vista.riferimentoAudio)
            pipeline.decodificatore.tutti(id)
        } ?: return RisultatoPipeline.Fallita(MOTIVO_DECODIFICA)

        pipeline.segnalatore.fase(id, DIARIZZAZIONE)
        val turni = eseguiFase { pipeline.diarizzatore.diarizza(campioni) }
            ?: return RisultatoPipeline.Fallita(MOTIVO_DIARIZZAZIONE)

        pipeline.segnalatore.fase(id, TRASCRIZIONE)
        val grezzi = eseguiFase { pipeline.allineatore.allinea(campioni, turni) }
            ?: return RisultatoPipeline.Fallita(MOTIVO_TRASCRIZIONE)

        pipeline.segnalatore.fase(id, ALLINEAMENTO)
        val segmenti = grezzi.map { SegmentoIniziale(it.voceIndice, it.intervallo, it.testo) } // AC-71: voceIndice kept
        return RisultatoPipeline.Successo(durataDecodificata(campioni), segmenti)
    }

    /**
     * Runs [blocco]; a fault of the port (I/O, native ML — ADR 0003) becomes `null`. Its technical
     * cause is dropped on purpose: the caller picks the one FIXED, plain-Italian `motivo` the user
     * sees (never raw exception text/paths/ids), and this codebase has no logging sink to hand it to.
     * Coroutine cancellation and thread interruption are never a "port fault": rethrown, the interrupt
     * flag restored — they must not be reported as a `fallita` Elaborazione.
     */
    private fun <T> eseguiFase(blocco: () -> T): T? = try {
        blocco()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        null
    }

    /**
     * True iff the transaction committed. Re-reads the Elaborazione BY ID first (the in-memory
     * instance mutated earlier is never reused): if it is no longer `in_corso`, something else already
     * concluded it (e.g. [RecuperaElaborazioniInterrotte] recovered it) — [seNonTerminale] never runs.
     * Shared by the completion transaction AND its compensation (same re-read/skip-if-terminale shape).
     */
    private fun concludi(
        registrazioneId: RegistrazioneId,
        elaborazioneId: ElaborazioneId,
        seNonTerminale: (Elaborazione) -> Esito<Unit>,
    ): Boolean = transazioneSicura {
        val fresca = elaborazioni.diRegistrazione(registrazioneId).firstOrNull { it.id == elaborazioneId }
        if (fresca == null || fresca.terminale) Esito.Ok(Unit) else seNonTerminale(fresca)
    }

    /**
     * Runs [blocco] in one transaction; a thrown fault is treated like a failed commit, never
     * Cancellation/Interrupted.
     */
    private fun transazioneSicura(blocco: () -> Esito<Unit>): Boolean = try {
        uow.inTransazione(blocco) is Esito.Ok
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        false
    }

    private fun concludiConSuccesso(
        elaborazione: Elaborazione,
        durataMs: Long,
        segmenti: List<SegmentoIniziale>,
    ): Esito<Unit> =
        when (val creato = Trascritto.crea(elaborazione.registrazioneId, durataMs, segmenti)) {
            is Esito.Ok -> completa(elaborazione, creato.valore)
            is Esito.Errore -> {
                val motivo = motivoTrascritto(creato.errore as ErroreTrascrizione)
                concludiConFallimento(elaborazione, motivo)
            }
        }

    private fun completa(elaborazione: Elaborazione, creato: Creato<Trascritto, TrascrittoCreato>): Esito<Unit> =
        elaborazione.completa().poi { evento ->
            trascritti.salva(creato.aggregato) // stessa transazione del salva sotto: INV-5
            elaborazioni.salva(elaborazione).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    /** Shared by a `fallita` outcome of the pipeline AND by [Trascritto.crea] refusing its input. */
    private fun concludiConFallimento(elaborazione: Elaborazione, motivo: String): Esito<Unit> =
        elaborazione.fallisci(motivo).poi { evento ->
            elaborazioni.salva(elaborazione).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    private companion object {
        const val MOTIVO_REGISTRAZIONE_MANCANTE = "registrazione non più disponibile"
        const val MOTIVO_DECODIFICA = "impossibile leggere l'audio"
        const val MOTIVO_DIARIZZAZIONE = "errore nella separazione delle voci"
        const val MOTIVO_TRASCRIZIONE = "errore nella trascrizione"
        const val MOTIVO_SALVATAGGIO_FALLITO = "salvataggio del risultato non riuscito"
        const val MOTIVO_NESSUN_PARLATO = "nessun parlato rilevato"
        const val MOTIVO_SEGMENTO_OLTRE_DURATA = "un segmento supera la durata della registrazione"
        const val MOTIVO_TRANSIZIONE_NON_AMMESSA = "transizione di stato non consentita"
        const val MOTIVO_ELABORAZIONE_GIA_APERTA = "un'altra elaborazione è già in corso per questa registrazione"
        const val MOTIVO_ELABORAZIONE_GIA_COMPLETATA = "questa registrazione ha già un risultato completato"
        const val MOTIVO_VOCE_NON_TROVATA = "voce non trovata"
        const val MOTIVO_SEGMENTO_NON_TROVATO = "segmento non trovato"
        const val MOTIVO_UNIONE_NON_AMMESSA = "unione di voci non consentita"
        const val MOTIVO_DIVISIONE_NON_AMMESSA = "divisione di voce non consentita"
        const val MOTIVO_RIASSEGNAZIONE_NON_AMMESSA = "riassegnazione del segmento non consentita"

        /** 16 kHz mono (`DecodificatoreAudio`, ADR 0005): samples per millisecond. */
        const val CAMPIONI_PER_MS = 16

        /**
         * Exhaustive, no `else`: a new [ErroreTrascrizione] variant breaks compilation here until it
         * gets its own plain-Italian `motivo` (ADR 0003). [Trascritto.crea] only ever returns
         * [ErroreTrascrizione.NessunParlatoRilevato] or [ErroreTrascrizione.SegmentoOltreLaDurata];
         * the other branches exist only so the mapping stays total for any future caller.
         */
        private fun motivoTrascritto(errore: ErroreTrascrizione): String = when (errore) {
            is ErroreTrascrizione.NessunParlatoRilevato -> MOTIVO_NESSUN_PARLATO
            is ErroreTrascrizione.SegmentoOltreLaDurata -> MOTIVO_SEGMENTO_OLTRE_DURATA
            is ErroreTrascrizione.TransizioneNonAmmessa -> MOTIVO_TRANSIZIONE_NON_AMMESSA
            is ErroreTrascrizione.ElaborazioneGiaAperta -> MOTIVO_ELABORAZIONE_GIA_APERTA
            is ErroreTrascrizione.ElaborazioneGiaCompletata -> MOTIVO_ELABORAZIONE_GIA_COMPLETATA
            is ErroreTrascrizione.VoceNonTrovata -> MOTIVO_VOCE_NON_TROVATA
            is ErroreTrascrizione.SegmentoNonTrovato -> MOTIVO_SEGMENTO_NON_TROVATO
            is ErroreTrascrizione.UnioneNonAmmessa -> MOTIVO_UNIONE_NON_AMMESSA
            is ErroreTrascrizione.DivisioneNonAmmessa -> MOTIVO_DIVISIONE_NON_AMMESSA
            is ErroreTrascrizione.RiassegnazioneNonAmmessa -> MOTIVO_RIASSEGNAZIONE_NON_AMMESSA
        }

        /**
         * Single source of truth for the duration passed to [Trascritto.crea]: the DECODED samples,
         * never the catalogue's `durataMs` (a rounding surplus there must not fail a run whose audio
         * actually decoded a little longer). Ceiling division: a trailing partial millisecond of
         * samples still counts as that millisecond.
         */
        private fun durataDecodificata(campioni: CampioniAudio): Long =
            (campioni.campioni.size.toLong() + CAMPIONI_PER_MS - 1) / CAMPIONI_PER_MS
    }
}

/** The pipeline's outcome (never inside a transaction): a Trascritto candidate, or a fixed `motivo`. */
private sealed interface RisultatoPipeline {
    data class Successo(val durataMs: Long, val segmenti: List<SegmentoIniziale>) : RisultatoPipeline
    data class Fallita(val motivo: String) : RisultatoPipeline
}

private fun snastro.trascrizione.dominio.ElaborazioneAvviata.pubblicato(): ElaborazioneAvviata =
    ElaborazioneAvviata(registrazioneId = registrazioneId, avviataAlle = avviataAlle)

private fun snastro.trascrizione.dominio.ElaborazioneCompletata.pubblicato(): ElaborazioneCompletata =
    ElaborazioneCompletata(registrazioneId = registrazioneId)

private fun snastro.trascrizione.dominio.ElaborazioneFallita.pubblicato(): ElaborazioneFallita =
    ElaborazioneFallita(registrazioneId = registrazioneId, motivo = motivo)
