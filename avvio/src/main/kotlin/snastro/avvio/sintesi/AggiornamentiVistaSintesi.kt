package snastro.avvio.sintesi

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
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
    private val annullaInCorso: (IncontroId) -> Unit,
    /** ADR 0033 §4.1: the Parti of an Incontro (each has its own S3 tab), `null` once it ceased. */
    private val partiDi: (IncontroId) -> List<RegistrazioneId>?,
) : AggiornamentiVista, AbbonatoDopoCommit {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    override fun ricevi(evento: EventoPubblicato) {
        val cambiamenti = when (evento) {
            is RiassuntoRichiesto -> cambiamentiDi(evento.incontroId).also { avanza() }
            is RiassuntoEliminato -> cambiamentiDi(evento.incontroId).also { annullaInCorso(evento.incontroId) }
            is RiassuntoAvviato -> cambiamentiDi(evento.incontroId)
            is RiassuntoPronto -> cambiamentiDi(evento.incontroId)
            is RiassuntoFallito -> cambiamentiDi(evento.incontroId)
            is LunghezzaMassimaRiassuntoModificata -> listOf(Cambiamento(null))
            else -> return
        }
        cambiamenti.forEach { _cambiamenti.tryEmit(it) }
    }

    /** One change per Parte of [incontroId]; an Incontro that ceased (its last Parte deleted) refreshes everything. */
    private fun cambiamentiDi(incontroId: IncontroId): List<Cambiamento> =
        partiDi(incontroId)?.map(::Cambiamento) ?: listOf(Cambiamento(null))

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
