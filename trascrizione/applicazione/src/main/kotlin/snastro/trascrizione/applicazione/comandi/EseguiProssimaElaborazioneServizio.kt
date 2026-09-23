package snastro.trascrizione.applicazione.comandi

import snastro.kernel.Creato
import snastro.kernel.DispatcherEventi
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
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

/**
 * Use-case `EseguiProssimaElaborazione` (AC-68..75, ADR 0004/0012): takes the oldest `in_attesa`
 * Elaborazione (FIFO), marks it `in_corso` in its own short transaction, then runs
 * decodifica → diarizzazione → trascrizione → allineamento through the pinned ML/audio ports of
 * [PortePipeline] OUTSIDE any transaction (AC-73), signalling each phase in order (AC-69). On success,
 * `completata` and the [Trascritto] are saved together in one short transaction (INV-5). Any failure —
 * a port fault (AC-70) or [Trascritto.crea] refusing the pipeline's output (AC-72, empty input) — ends
 * as `fallita(motivo)` in its own short transaction, with no Trascritto ever saved. Never decodes the
 * whole Registrazione as an interval: [PortePipeline.decodificatore]`.tutti` reads the whole audio,
 * `.campioni(intervallo)` is for turn/Segmento-scoped work elsewhere (review finding). Startup
 * counterpart for a run this leaves `in_corso`: [RecuperaElaborazioniInterrotte].
 */
public class EseguiProssimaElaborazioneServizio(
    private val uow: UnitaDiLavoro,
    private val orologio: Clock,
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: TrascrittoRepository,
    private val pipeline: PortePipeline,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(ignored: EseguiProssimaElaborazione): Esito<Unit> {
        val elaborazione = elaborazioni.inAttesa().firstOrNull() ?: return Esito.Ok(Unit) // AC-68
        return eseguiSu(elaborazione)
    }

    private fun eseguiSu(elaborazione: Elaborazione): Esito<Unit> {
        val avviata = uow.inTransazione { avvia(elaborazione) }
        if (avviata is Esito.Errore) return avviata

        val id = elaborazione.registrazioneId
        val esitoPipeline = runCatching { eseguiPipeline(id) }

        val esitoFinale = uow.inTransazione {
            esitoPipeline.fold(
                onSuccess = { (durataMs, segmenti) -> concludiConSuccesso(elaborazione, durataMs, segmenti) },
                onFailure = { e -> concludiConFallimento(elaborazione, motivoPorta(e)) },
            )
        }
        pipeline.segnalatore.terminata(id)
        return esitoFinale
    }

    private fun avvia(elaborazione: Elaborazione): Esito<Unit> =
        elaborazione.avvia(orologio.instant()).poi { evento ->
            elaborazioni.salva(elaborazione).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    /** Runs the pipeline (never inside a transaction) and returns the Registrazione's duration + Segmenti. */
    private fun eseguiPipeline(id: RegistrazioneId): Pair<Long, List<SegmentoIniziale>> {
        val vista = pipeline.registrazioni.registrazione(id) ?: error("registrazione ${id.valore} non trovata")

        pipeline.segnalatore.fase(id, DECODIFICA)
        pipeline.decodificatore.decodifica(id, vista.riferimentoAudio)
        val campioni = pipeline.decodificatore.tutti(id)

        pipeline.segnalatore.fase(id, DIARIZZAZIONE)
        val turni = pipeline.diarizzatore.diarizza(campioni)

        pipeline.segnalatore.fase(id, TRASCRIZIONE)
        val grezzi = pipeline.allineatore.allinea(campioni, turni)

        pipeline.segnalatore.fase(id, ALLINEAMENTO)
        val segmenti = grezzi.map { SegmentoIniziale(it.voceIndice, it.intervallo, it.testo) } // AC-71: voceIndice kept

        return vista.durataMs to segmenti
    }

    private fun concludiConSuccesso(
        elaborazione: Elaborazione,
        durataMs: Long,
        segmenti: List<SegmentoIniziale>,
    ): Esito<Unit> =
        when (val creato = Trascritto.crea(elaborazione.registrazioneId, durataMs, segmenti)) {
            is Esito.Ok -> completa(elaborazione, creato.valore)
            is Esito.Errore -> concludiConFallimento(elaborazione, motivoTrascritto(creato.errore))
        }

    private fun completa(elaborazione: Elaborazione, creato: Creato<Trascritto, TrascrittoCreato>): Esito<Unit> =
        elaborazione.completa().poi { evento ->
            trascritti.salva(creato.aggregato)
            elaborazioni.salva(elaborazione).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    private fun concludiConFallimento(elaborazione: Elaborazione, motivo: String): Esito<Unit> =
        elaborazione.fallisci(motivo).poi { evento ->
            elaborazioni.salva(elaborazione).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    private companion object {
        const val MOTIVO_NESSUN_PARLATO = "nessun parlato rilevato"
        const val MOTIVO_PORTA_DI_DEFAULT = "errore imprevisto durante l'elaborazione"

        private fun motivoPorta(e: Throwable): String =
            e.message?.takeIf { it.isNotBlank() } ?: MOTIVO_PORTA_DI_DEFAULT

        private fun motivoTrascritto(errore: ErroreDominio): String = when (errore) {
            is ErroreTrascrizione.NessunParlatoRilevato -> MOTIVO_NESSUN_PARLATO
            is ErroreTrascrizione.SegmentoOltreLaDurata ->
                "un segmento supera la durata della registrazione (${errore.durataMs} ms)"
            else -> error("errore inatteso da Trascritto.crea: $errore")
        }
    }
}

private fun snastro.trascrizione.dominio.ElaborazioneAvviata.pubblicato(): ElaborazioneAvviata =
    ElaborazioneAvviata(registrazioneId = registrazioneId, avviataAlle = avviataAlle)

private fun snastro.trascrizione.dominio.ElaborazioneCompletata.pubblicato(): ElaborazioneCompletata =
    ElaborazioneCompletata(registrazioneId = registrazioneId)

private fun snastro.trascrizione.dominio.ElaborazioneFallita.pubblicato(): ElaborazioneFallita =
    ElaborazioneFallita(registrazioneId = registrazioneId, motivo = motivo)
