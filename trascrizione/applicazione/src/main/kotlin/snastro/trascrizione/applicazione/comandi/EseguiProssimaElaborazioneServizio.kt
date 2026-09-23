package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.Creato
import snastro.kernel.DispatcherEventi
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDominio
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
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
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
 * marks it `fallita`. If the compensation itself fails too, its fault PROPAGATES to the caller with
 * the original fault attached (`addSuppressed`) — never dropped — and the Elaborazione is left
 * `in_corso`: [RecuperaElaborazioniInterrotte] (startup) recovers it as `fallita('interrotta')`.
 * [PortePipeline.segnalatore]`.terminata` always fires, in a `finally`, whatever the outcome. Never
 * decodes the whole Registrazione as an interval: [PortePipeline.decodificatore]`.tutti` reads the
 * whole audio, `.campioni(intervallo)` is for turn/Segmento-scoped work elsewhere (review finding).
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
        try {
            val risultato = eseguiPipeline(registrazioneId)
            val esitoFinale = inTransazioneTentata {
                concludi(registrazioneId, elaborazioneId) { fresca ->
                    when (risultato) {
                        is RisultatoPipeline.Successo ->
                            concludiConSuccesso(fresca, risultato.durataMs, risultato.segmenti)
                        is RisultatoPipeline.Fallita -> concludiConFallimento(fresca, risultato.motivo)
                    }
                }
            }
            // F2: la transazione finale e' fallita (Errore o eccezione) -> compensazione a fallita.
            if (esitoFinale !is TransazioneFinale.Confermata) compensa(registrazioneId, elaborazioneId, esitoFinale)
            return Esito.Ok(Unit)
        } finally {
            pipeline.segnalatore.terminata(registrazioneId) // sempre segnalata, qualunque sia l'esito
        }
    }

    /**
     * Runs the pipeline (never inside a transaction, AC-73) up to a [RisultatoPipeline]. EVERY port
     * call — the [PortePipeline.registrazioni] lookup and each [PortePipeline.segnalatore]`.fase` signal
     * included — runs inside [eseguiFase], so a fault of any port in any phase becomes `fallita` with
     * that step's fixed `motivo` (AC-70), never an exception escaping with the Elaborazione left
     * `in_corso`. A throwing `fase` signal is FATAL like any other port fault (its contract,
     * `SegnalatoreFaseContratto`, says it never throws; if it does, dropping the fault would be a
     * swallowed failure, CR-7). A lookup MISS (`null`) is not a fault: its own motivo (F7). One guard
     * clause per step (readable, each mapped to its own fixed `motivo`) — `@Suppress`: deliberate.
     */
    @Suppress("ReturnCount")
    private fun eseguiPipeline(id: RegistrazioneId): RisultatoPipeline {
        val letta = eseguiFase { Lettura(pipeline.registrazioni.registrazione(id)) }
            ?: return RisultatoPipeline.Fallita(MOTIVO_LETTURA_REGISTRAZIONE)
        val vista = letta.vista ?: return RisultatoPipeline.Fallita(MOTIVO_REGISTRAZIONE_MANCANTE)

        val campioni = eseguiFase {
            pipeline.segnalatore.fase(id, DECODIFICA)
            pipeline.decodificatore.decodifica(id, vista.riferimentoAudio)
            pipeline.decodificatore.tutti(id)
        } ?: return RisultatoPipeline.Fallita(MOTIVO_DECODIFICA)

        val turni = eseguiFase {
            pipeline.segnalatore.fase(id, DIARIZZAZIONE)
            pipeline.diarizzatore.diarizza(campioni)
        } ?: return RisultatoPipeline.Fallita(MOTIVO_DIARIZZAZIONE)

        val grezzi = eseguiFase {
            pipeline.segnalatore.fase(id, TRASCRIZIONE)
            pipeline.allineatore.allinea(campioni, turni)
        } ?: return RisultatoPipeline.Fallita(MOTIVO_TRASCRIZIONE)

        eseguiFase { pipeline.segnalatore.fase(id, ALLINEAMENTO) }
            ?: return RisultatoPipeline.Fallita(MOTIVO_ALLINEAMENTO)
        val segmenti = grezzi.map { SegmentoIniziale(it.voceIndice, it.intervallo, it.testo) } // AC-71: voceIndice kept
        return RisultatoPipeline.Successo(durataDecodificata(campioni), segmenti)
    }

    /**
     * Re-reads the Elaborazione BY ID first (the in-memory instance mutated earlier is never reused):
     * if it is no longer `in_corso`, something else already concluded it (e.g.
     * [RecuperaElaborazioniInterrotte] recovered it) — [seNonTerminale] never runs. Shared by the
     * completion transaction AND its compensation (same re-read/skip-if-terminale shape).
     */
    private fun concludi(
        registrazioneId: RegistrazioneId,
        elaborazioneId: ElaborazioneId,
        seNonTerminale: (Elaborazione) -> Esito<Unit>,
    ): Esito<Unit> {
        val fresca = elaborazioni.diRegistrazione(registrazioneId).firstOrNull { it.id == elaborazioneId }
        return if (fresca == null || fresca.terminale) Esito.Ok(Unit) else seNonTerminale(fresca)
    }

    /**
     * Runs the completion transaction; a refusal ([Esito.Errore]) or a thrown fault is KEPT (not
     * dropped) so that, should the compensation fail too, it travels with it ([compensa]).
     * Cancellation/Interrupted are rethrown, an [Error] is never caught.
     */
    private fun inTransazioneTentata(blocco: () -> Esito<Unit>): TransazioneFinale = try {
        when (val esito = uow.inTransazione(blocco)) {
            is Esito.Ok -> TransazioneFinale.Confermata
            is Esito.Errore -> TransazioneFinale.Rifiutata(esito)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        TransazioneFinale.Lanciata(e)
    }

    /**
     * F-B: the COMPENSATING transaction (re-read by id, `fallita` with a fixed motivo). If it fails
     * too, the fault never vanishes: its exception propagates — a refusal becomes a
     * [TransazioneRifiutata] — with the ORIGINAL fault of [originale] attached via `addSuppressed`,
     * so the dispatcher sees both; `terminata` has already fired in [eseguiSu]'s `finally`. The
     * Elaborazione is then left `in_corso` and recovered by [RecuperaElaborazioniInterrotte] (startup).
     */
    private fun compensa(
        registrazioneId: RegistrazioneId,
        elaborazioneId: ElaborazioneId,
        originale: TransazioneFinale,
    ) {
        val esito = try {
            uow.inTransazione {
                concludi(registrazioneId, elaborazioneId) { concludiConFallimento(it, MOTIVO_SALVATAGGIO_FALLITO) }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // rethrown below, only enriched
        ) {
            originale.comeEccezione()?.takeIf { it !== e }?.let(e::addSuppressed) // no self-suppression
            throw e
        }
        if (esito is Esito.Errore) {
            throw TransazioneRifiutata("compensazione", esito.errore).apply {
                originale.comeEccezione()?.let(::addSuppressed)
            }
        }
    }

    private fun concludiConSuccesso(
        elaborazione: Elaborazione,
        durataMs: Long,
        segmenti: List<SegmentoIniziale>,
    ): Esito<Unit> =
        when (val creato = Trascritto.crea(elaborazione.registrazioneId, durataMs, segmenti)) {
            is Esito.Ok -> completa(elaborazione, creato.valore)
            is Esito.Errore -> {
                // F-D: Trascritto.crea only returns ErroreTrascrizione; any other ErroreDominio is a
                // programmer error, surfaced with its OWN motivo — never disguised as another failure.
                val errore = creato.errore as? ErroreTrascrizione
                concludiConFallimento(elaborazione, errore?.let(::motivoTrascritto) ?: MOTIVO_ERRORE_INTERNO)
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
        const val MOTIVO_LETTURA_REGISTRAZIONE = "impossibile leggere i dati della registrazione"
        const val MOTIVO_DECODIFICA = "impossibile leggere l'audio"
        const val MOTIVO_DIARIZZAZIONE = "errore nella separazione delle voci"
        const val MOTIVO_TRASCRIZIONE = "errore nella trascrizione"
        const val MOTIVO_ALLINEAMENTO = "errore nell'allineamento del testo"
        const val MOTIVO_ERRORE_INTERNO = "errore interno imprevisto"
        const val MOTIVO_SALVATAGGIO_FALLITO = "salvataggio del risultato non riuscito"
        const val MOTIVO_NESSUN_PARLATO = "nessun parlato rilevato"
        const val MOTIVO_SEGMENTO_OLTRE_DURATA = "un segmento supera la durata della registrazione"
        const val MOTIVO_TRANSIZIONE_NON_AMMESSA = "transizione di stato non consentita"
        const val MOTIVO_ELABORAZIONE_GIA_APERTA = "un'altra elaborazione è già in corso per questa registrazione"
        const val MOTIVO_ELABORAZIONE_GIA_COMPLETATA = "questa registrazione ha già un risultato completato"
        const val MOTIVO_TRASCRITTO_NON_TROVATO = "trascrizione non ancora disponibile"
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
            is ErroreTrascrizione.RegistrazioneNonTrovata -> MOTIVO_REGISTRAZIONE_MANCANTE
            is ErroreTrascrizione.TrascrittoNonTrovato -> MOTIVO_TRASCRITTO_NON_TROVATO
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

/**
 * Runs [blocco]; a fault of the port (I/O, native ML — ADR 0003) becomes `null`. Its technical
 * cause is dropped on purpose: the caller picks the one FIXED, plain-Italian `motivo` the user
 * sees (never raw exception text/paths/ids), and this codebase has no logging sink to hand it to.
 * Coroutine cancellation and thread interruption are never a "port fault": rethrown, the interrupt
 * flag restored — they must not be reported as a `fallita` Elaborazione. An [Error] is never caught.
 */
private fun <T : Any> eseguiFase(blocco: () -> T): T? = try {
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

/** The Registrazione lookup's outcome: [vista] `null` is a MISS (F7), distinct from a port fault. */
private class Lettura(val vista: RegistrazioneVista?)

/** Outcome of the completion transaction; a failure keeps its fault for [TransazioneFinale.comeEccezione]. */
private sealed interface TransazioneFinale {
    data object Confermata : TransazioneFinale
    data class Rifiutata(val esito: Esito.Errore) : TransazioneFinale
    data class Lanciata(val eccezione: Exception) : TransazioneFinale

    /** The original fault as a Throwable to attach (suppressed) to a failing compensation. */
    fun comeEccezione(): Throwable? = when (this) {
        Confermata -> null
        is Rifiutata -> TransazioneRifiutata("transazione finale", esito.errore)
        is Lanciata -> eccezione
    }
}

/** A transaction refused with an [Esito.Errore], as a Throwable for the dispatcher (never user-facing). */
private class TransazioneRifiutata(quale: String, errore: ErroreDominio) :
    IllegalStateException("$quale rifiutata: $errore")

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
