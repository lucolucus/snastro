package snastro.avvio.trascrizione

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.eventi.ElaborazioneAnnullata
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * AC-354: the Trascrizione half of S2's [AggiornamentiVista] (merged with the others by `apriProgetto`). An
 * [AbbonatoDopoCommit] VALUE that `ModuloTrascrizione` pairs (ADR 0030 §1) — after commit, never on rollback — with
 * `ElaborazioneAvviata`/`Completata`/`Fallita`/`Annullata` (ADR 0018 Amendment (b), AC-478: S2 reloads the whole
 * list, so every other queued row's position too, and S3 leaves read-only), `TrascrittoSostituito` (ADR 0018),
 * `VociUnite`, `VoceDivisa`, `SegmentoRiassegnato` (one per Parte of their Incontro); and [cambiata] is what every
 * phase
 * change of the shared `FasiInCorso` calls ([SegnalatoreFaseConCambiamenti]). Each produces one [Cambiamento] for its
 * Registrazione, so S2
 * updates state and phase without polling. `replay = 1`: same reason as `AggiornamentiVistaEventi` (a screen mounted
 * right after a change still refreshes once).
 */
internal class AggiornamentiVistaTrascrizione(
    /** ADR 0033 §4.1: the Parti of an Incontro (a Revisione event names the Incontro), `null` once it ceased. */
    private val partiDi: (IncontroId) -> List<RegistrazioneId>?,
) : AggiornamentiVista, AbbonatoDopoCommit {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    override fun ricevi(evento: EventoPubblicato) {
        registrazioniDi(evento).forEach(::cambiata)
    }

    /** Emits a [Cambiamento] for [id]; never blocks (a slow collector only loses intermediate duplicates). */
    fun cambiata(id: RegistrazioneId) {
        _cambiamenti.tryEmit(Cambiamento(id))
    }

    private fun registrazioniDi(evento: EventoPubblicato): List<RegistrazioneId> = when (evento) {
        is ElaborazioneAvviata -> listOf(evento.registrazioneId)
        is ElaborazioneCompletata -> listOf(evento.registrazioneId)
        is ElaborazioneFallita -> listOf(evento.registrazioneId)
        is ElaborazioneAnnullata -> listOf(evento.registrazioneId)
        is TrascrittoSostituito -> listOf(evento.registrazioneId)
        // ADR 0035 §5: a Revisione is keyed by its Incontro; every Parte of it refreshes.
        is VociUnite -> partiDi(evento.incontroId).orEmpty()
        is VoceDivisa -> partiDi(evento.incontroId).orEmpty()
        is SegmentoRiassegnato -> partiDi(evento.incontroId).orEmpty()
        else -> emptyList()
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
