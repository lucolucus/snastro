package snastro.avvio.parlanti

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
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
 * The Parlanti half of the open project's [AggiornamentiVista] (merged with the others by `apriProgetto`), and the
 * [ProposteSerializzate] invalidation — ONE [AbbonatoDopoCommit] VALUE (after commit, never on rollback) that
 * `ModuloParlanti` pairs with the events below; the declared after-commit order puts Parlanti BEFORE Trascrizione, so
 * a Proposta is always invalidated before any screen hears of the change that made it stale (AC-173, AC-317):
 *
 * - `AttribuzioneConfermata` → invalidate, `Cambiamento(its Registrazione)` (S2 badge, S3 panel, S4);
 * - `ImpronteRiallineate` → invalidate, `Cambiamento` of each Parte of its Incontro (AC-317; the Sbobinatura does not
 *   subscribe to it: prints do not change a Sbobinatura);
 * - `ParlanteCreato`/`Rinominato`/`Promosso`/`Eliminato` → invalidate, `Cambiamento(null)` (a Nome may show
 *   in every Registrazione);
 * - `TrascrittoSostituito` (ADR 0018 §5, AC-456) → invalidate, `Cambiamento(null)`: cached Proposte are keyed
 *   by `VoceRef` and now point at the wrong Voci, the Galleria counts of any Parlante may have changed and an
 *   `occasionale` may be gone (the purge itself ran synchronously, inside the completion transaction);
 * - `RegistrazioneEliminata` (ADR 0020 §5, AC-631) → invalidate, `Cambiamento(null)`: the same reasons — its
 *   Voci and prints are gone (purged synchronously in the deleting transaction), S2 loses the row, S4 counts drop;
 * - `VociUnite`/`VoceDivisa`/`SegmentoRiassegnato` → invalidate only (`AggiornamentiVistaTrascrizione`
 *   already emits their `Cambiamento`);
 * - `SegmentoConfermato` (ADR 0019 §3, after commit only) → invalidate, `Cambiamento(its Registrazione)`: the
 *   S3 pin and the similarity button's reference lines (Trascrizione's own subscriber has no `Cambiamento` for it).
 *
 * `replay = 1`: same reason as `AggiornamentiVistaEventi`.
 */
internal class AggiornamentiVistaParlanti(
    private val proposte: ProposteSerializzate,
    /** ADR 0033 §4.1: the Parti of an Incontro (an Attribuzione is per Voce of the Incontro), `null` once it ceased. */
    private val partiDi: (IncontroId) -> List<RegistrazioneId>?,
) : AggiornamentiVista, AbbonatoDopoCommit {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    override fun ricevi(evento: EventoPubblicato) {
        val cambiamenti = when (evento) {
            is AttribuzioneConfermata ->
                partiDi(evento.voceRef.incontroId)?.map(::Cambiamento) ?: listOf(Cambiamento(null))
            is ImpronteRiallineate ->
                partiDi(evento.incontroId)?.map(::Cambiamento) ?: listOf(Cambiamento(null))
            is ParlanteCreato, is ParlanteRinominato, is ParlantePromosso, is ParlanteEliminato,
            is TrascrittoSostituito, is RegistrazioneEliminata,
            -> listOf(Cambiamento(null))
            is SegmentoConfermato -> listOf(Cambiamento(evento.registrazioneId))
            is VociUnite, is VoceDivisa, is SegmentoRiassegnato -> emptyList()
            else -> return
        }
        proposte.invalidaTutte()
        cambiamenti.forEach(_cambiamenti::tryEmit)
    }

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
