package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ProgettoId
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio
import java.nio.file.Path

/**
 * The single seam through which a later release EXTENDS the R0 per-project graph built by
 * [SessioneProgettoImpl] (avvio-composizione: "extends avvio-r0's graph, never re-creates
 * SessioneProgetto, the dispatcher or LettoreAudio"). R0 passes none (`null`): nothing beyond R0 is
 * built. R1 (`snastro.avvio.r1.EstensioneR1`) builds the Trascrizione/Documento graph of the project
 * just opened, over the SAME database, dispatcher and session scope R0 already owns. R2
 * (`snastro.avvio.r2.EstensioneR2`) wraps R1's and adds the Parlanti graph the same way.
 *
 * Typed over R0-owned values only — this file (like every file outside `snastro.avvio.r1`/`snastro.avvio.r2`) never
 * imports a Trascrizione/Documento/Modelli/Parlanti type (`GrafoR0Test`, AC-350 guard scoped to the R0 graph).
 */
internal fun interface EstensioneSessione {
    /** Builds the extension for the project described by [contesto]; called once per open/create. */
    fun apri(contesto: ContestoEstensione): ProgettoEsteso
}

/** What [SessioneProgettoImpl] hands an [EstensioneSessione] for one open project. */
@Suppress("LongParameterList") // one parameter per R0-owned value the extension builds over
internal class ContestoEstensione(
    /** The open Progetto (R2: its Parlanti, `RiallineaTutteLeImpronte`). */
    val progettoId: ProgettoId,
    val cartella: Path,
    val database: SnastroDatabase,
    val dispatcher: DispatcherEventiInMemoria,
    /** The session's own child scope — cancelled by [SessioneProgettoImpl.chiudi] BEFORE [ProgettoEsteso.ferma]. */
    val scope: CoroutineScope,
    val registrazioni: RegistrazioneRepository,
    /** The project's ONE player (R0's): R2 pauses it when the Registrazione it holds is deleted (ADR 0020 §3). */
    val lettoreAudio: LettoreAudio,
)

/** The extension's per-project state, as far as R0's own lifecycle needs to know it. */
internal interface ProgettoEsteso {
    /** Merged into the project's [CollaboratoriProgettoAperto.aggiornamentiVista]. */
    val aggiornamenti: AggiornamentiVista

    /**
     * Blocking stop, called by [SessioneProgettoImpl.chiudi] (off the UI thread) AFTER the session
     * scope was cancelled: waits (bounded) for every background worker still touching the database to
     * unwind, then runs [poi] — the database close and the `.lock` release. fix-batch-16 MED-1: [poi]
     * runs right away only if every worker actually ended in time; otherwise it is DEFERRED to the end
     * of the last one still alive (a native call ignores the interrupt), never run under a live worker.
     * [poi] runs exactly once. Never throws on a timeout.
     */
    fun ferma(poi: () -> Unit)
}
