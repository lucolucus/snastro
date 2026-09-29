package snastro.avvio.sintesi

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.AbbonatoDopoCommit
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
 * AC-S144: the Sintesi half of the open project's [AggiornamentiVista], ONE [AbbonatoDopoCommit] VALUE that
 * `ModuloSintesi` pairs with the Sintesi events (after commit only, never on rollback; ADR 0021 §3 — Sintesi
 * events have NO synchronous subscriber):
 * - every Sintesi event of a Registrazione → `Cambiamento(registrazioneId)`;
 * - `LunghezzaMassimaRiassuntoModificata` → `Cambiamento(null)` (the setting shows on every Riassunto tab);
 * - `RiassuntoRichiesto` → also [avanza] (the shared queue's [snastro.avvio.coda.Campanello], ADR 0023 §2);
 * - `RiassuntoEliminato` → also [annullaInCorso] (the best-effort cancellation of that Registrazione's running
 *   Riassunto, ADR 0023 §5 — matched and guarded by the Riassunto source's own per-run state, AC-S63/AC-S161).
 *
 * Idempotent: a duplicate event only re-signals / re-reads. `replay = 1`: as `AggiornamentiVistaEventi`.
 */
internal class AggiornamentiVistaSintesi(
    private val avanza: () -> Unit,
    private val annullaInCorso: (RegistrazioneId) -> Unit,
) : AggiornamentiVista, AbbonatoDopoCommit {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    override fun ricevi(evento: EventoPubblicato) {
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
