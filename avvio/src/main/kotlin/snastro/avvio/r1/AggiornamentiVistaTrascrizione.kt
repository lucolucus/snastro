package snastro.avvio.r1

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * AC-354: the Trascrizione half of S2's [AggiornamentiVista] (merged with R0's own by
 * `SessioneProgettoImpl`). Registers itself as an `AbbonatoDopoCommit` of [dispatcher] — after
 * commit, never on rollback — for `ElaborazioneAvviata`/`Completata`/`Fallita`, `VociUnite`,
 * `VoceDivisa`, `SegmentoRiassegnato`; and [cambiata] is what every phase change of the shared
 * `FasiInCorso` calls ([SegnalatoreFaseConCambiamenti]). Each produces one [Cambiamento] for its
 * Registrazione, so S2 updates state and phase without polling. `replay = 1`: same reason as R0's
 * `AggiornamentiVistaEventi` (a screen mounted right after a change still refreshes once).
 */
internal class AggiornamentiVistaTrascrizione(dispatcher: DispatcherEventiInMemoria) : AggiornamentiVista {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    init {
        dispatcher.registraDopoCommit { evento -> registrazioneDi(evento)?.let(::cambiata) }
    }

    /** Emits a [Cambiamento] for [id]; never blocks (a slow collector only loses intermediate duplicates). */
    fun cambiata(id: RegistrazioneId) {
        _cambiamenti.tryEmit(Cambiamento(id))
    }

    private fun registrazioneDi(evento: EventoPubblicato): RegistrazioneId? = when (evento) {
        is ElaborazioneAvviata -> evento.registrazioneId
        is ElaborazioneCompletata -> evento.registrazioneId
        is ElaborazioneFallita -> evento.registrazioneId
        is VociUnite -> evento.registrazioneId
        is VoceDivisa -> evento.registrazioneId
        is SegmentoRiassegnato -> evento.registrazioneId
        else -> null
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
