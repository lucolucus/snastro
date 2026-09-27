package snastro.avvio.r3

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.eventi.LunghezzaMassimaRiassuntoModificata
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * AC-S144: the Sintesi half of the open project's [AggiornamentiVista], ONE `AbbonatoDopoCommit` of [dispatcher]
 * (after commit only, never on rollback; ADR 0021 §3 — Sintesi events have NO synchronous subscriber):
 * - every Sintesi event of a Registrazione → `Cambiamento(registrazioneId)`;
 * - `LunghezzaMassimaRiassuntoModificata` → `Cambiamento(null)` (the setting shows on every Riassunto tab);
 * - `RiassuntoRichiesto` → also [avanza] (the shared queue's signal, ADR 0023 §2);
 * - `RiassuntoEliminato` → also [annullaInCorso] (the queue's best-effort cancellation of that Registrazione's
 *   running Riassunto, ADR 0023 §5 — guarded by the source itself, AC-S161).
 *
 * Idempotent: a duplicate event only re-signals / re-reads. `replay = 1`: as R0's `AggiornamentiVistaEventi`.
 */
internal class AggiornamentiVistaSintesi(
    dispatcher: DispatcherEventiInMemoria,
    private val avanza: () -> Unit,
    private val annullaInCorso: (RegistrazioneId) -> Unit,
) : AggiornamentiVista {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    init {
        dispatcher.registraDopoCommit(::ricevi)
    }

    private fun ricevi(evento: EventoPubblicato) {
        val cambiamento = when (evento) {
            is RiassuntoRichiesto -> Cambiamento(evento.registrazioneId).also { avanza() }
            is RiassuntoEliminato -> Cambiamento(evento.registrazioneId).also { annullaInCorso(evento.registrazioneId) }
            is RiassuntoAvviato -> Cambiamento(evento.registrazioneId)
            is RiassuntoPronto -> Cambiamento(evento.registrazioneId)
            is RiassuntoFallito -> Cambiamento(evento.registrazioneId)
            is LunghezzaMassimaRiassuntoModificata -> Cambiamento(null)
            else -> return
        }
        _cambiamenti.tryEmit(cambiamento)
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
