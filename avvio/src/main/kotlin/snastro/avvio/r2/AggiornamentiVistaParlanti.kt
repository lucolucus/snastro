package snastro.avvio.r2

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.EventoPubblicato
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.eventi.ParlanteEliminato
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * The Parlanti half of the open project's [AggiornamentiVista] (merged with R0's and R1's), and the
 * [ProposteSerializzate] invalidation — ONE `AbbonatoDopoCommit` of [dispatcher] (after commit, never on
 * rollback), registered by `EstensioneR2` BEFORE R1's own subscribers so a Proposta is always invalidated
 * before any screen hears of the change that made it stale (AC-173 wiring, AC-317):
 *
 * - `AttribuzioneConfermata` → invalidate, `Cambiamento(its Registrazione)` (S2 badge, S3 panel, S4);
 * - `ImpronteRiallineate` → invalidate, `Cambiamento(its Registrazione)` (AC-317; the Documento does not
 *   subscribe to it: prints do not change a Documento);
 * - `ParlanteCreato`/`Rinominato`/`Promosso`/`Eliminato` → invalidate, `Cambiamento(null)` (a Nome may show
 *   in every Registrazione);
 * - `TrascrittoSostituito` (ADR 0018 §5, AC-456) → invalidate, `Cambiamento(null)`: cached Proposte are keyed
 *   by `VoceRef` and now point at the wrong Voci, the Galleria counts of any Parlante may have changed and an
 *   `occasionale` may be gone (the purge itself ran synchronously, inside the completion transaction);
 * - `RegistrazioneEliminata` (ADR 0020 §5, AC-631) → invalidate, `Cambiamento(null)`: the same reasons — its
 *   Voci and prints are gone (purged synchronously in the deleting transaction), S2 loses the row, S4 counts drop;
 * - `VociUnite`/`VoceDivisa`/`SegmentoRiassegnato` → invalidate only (R1's `AggiornamentiVistaTrascrizione`
 *   already emits their `Cambiamento`);
 * - `SegmentoConfermato` (ADR 0019 §3, after commit only) → invalidate, `Cambiamento(its Registrazione)`: the
 *   S3 pin and the similarity button's reference lines (R1 has no confirmation, so no R1 `Cambiamento`).
 *
 * `replay = 1`: same reason as R0's `AggiornamentiVistaEventi`.
 */
internal class AggiornamentiVistaParlanti(
    dispatcher: DispatcherEventiInMemoria,
    private val proposte: ProposteSerializzate,
) : AggiornamentiVista {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
    }

    private fun ricevi(evento: EventoPubblicato) {
        val cambiamento = when (evento) {
            is AttribuzioneConfermata -> Cambiamento(evento.voceRef.registrazioneId)
            is ImpronteRiallineate -> Cambiamento(evento.registrazioneId)
            is ParlanteCreato, is ParlanteRinominato, is ParlantePromosso, is ParlanteEliminato,
            is TrascrittoSostituito, is RegistrazioneEliminata,
            -> Cambiamento(null)
            is SegmentoConfermato -> Cambiamento(evento.registrazioneId)
            is VociUnite, is VoceDivisa, is SegmentoRiassegnato -> null
            else -> return
        }
        proposte.invalidaTutte()
        cambiamento?.let(_cambiamenti::tryEmit)
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
