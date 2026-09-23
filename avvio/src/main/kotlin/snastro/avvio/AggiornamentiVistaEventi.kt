package snastro.avvio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * [AggiornamentiVista] fed by `RegistrazioneAggiunta`/`DataRegistrazioneModificata`/
 * `RegistrazioneRinominata` (AC-242, AC-366 — R0's Registrazione commands): registers itself as an
 * [snastro.kernel.AbbonatoDopoCommit] of [dispatcher] —
 * AFTER commit, never on rollback, like every consumer of `RegistrazioneAggiunta` (it has no
 * synchronous subscriber: importing never auto-starts an Elaborazione, that policy is removed, not
 * deferred — ADR 0014). `replay = 1`: a collector that starts AFTER a
 * [Cambiamento] already fired (a screen mounted between the commit and its own `init`) still sees it
 * once and refreshes — never stuck showing a stale list.
 */
internal class AggiornamentiVistaEventi(dispatcher: DispatcherEventiInMemoria) : AggiornamentiVista {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
    }

    private fun ricevi(evento: EventoPubblicato) {
        val registrazioneId: RegistrazioneId = when (evento) {
            is RegistrazioneAggiunta -> evento.registrazioneId
            is DataRegistrazioneModificata -> evento.registrazioneId
            is RegistrazioneRinominata -> evento.registrazioneId
            else -> return
        }
        _cambiamenti.tryEmit(Cambiamento(registrazioneId))
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
