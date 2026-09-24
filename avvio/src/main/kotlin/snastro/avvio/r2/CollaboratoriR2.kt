package snastro.avvio.r2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import snastro.avvio.ProgettoEsteso
import snastro.avvio.r1.CollaboratoriR1
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.PromuoviParlante
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.letture.ConteggioIdentificazione
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.ParlanteDelProgetto
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento
import java.util.logging.Level
import java.util.logging.Logger

/** The Parlanti read-models of the open project, as plain functions (CR-1: `:ui` binds function types). */
@Suppress("LongParameterList") // one parameter per read-model S2/S3/S4 bind
internal class LettureParlanti(
    val identificazione: (RegistrazioneId) -> List<VoceIdentificata>,
    val proposta: (VoceRef) -> PropostaVista?,
    val unioni: (RegistrazioneId) -> List<PropostaDiUnione>,
    val parlantiAttivi: () -> List<ParlanteAttivo>,
    val estratto: (VoceRef) -> EstrattoRef?,
    val parlantiDelProgetto: () -> List<ParlanteDelProgetto>,
    val identificazioni: (List<RegistrazioneId>) -> List<ConteggioIdentificazione>,
)

/** S4's commands of the open project, each built with `eventi.unitaDiLavoro` (AC-359). */
internal class ComandiParlante(
    val rinomina: (RinominaParlante) -> Esito<Unit>,
    val promuovi: (PromuoviParlante) -> Esito<Unit>,
    val elimina: (EliminaParlante) -> Esito<Unit>,
)

/**
 * The R2 (Parlanti) collaborators of ONE open project, built by [EstensioneR2] over R1's own ([r1]) — the
 * Trascrizione sources, the Revisione commands, the queue and the Documento worker are R1's, never rebuilt.
 *
 * [lavoro] is the parent of every R2 worker of this project: the per-project [comandi] scope (AC-418), the
 * `AbbonatoRiallineamentoImpronte` loop, the `RiallineaTutteLeImpronte` job, and every S3 screen scope
 * ([scopeSchermata]) — so the S3 Proposta job too (AC-421). It is a child of the session scope: closing
 * the project cancels all of them at once.
 */
@Suppress("LongParameterList") // one parameter per per-project collaborator
internal class CollaboratoriR2(
    val r1: CollaboratoriR1,
    val letture: LettureParlanti,
    val comandi: ComandiVoceProgetto,
    val comandiParlante: ComandiParlante,
    val lavoro: Job,
    aggiornamentiParlanti: AggiornamentiVista,
    private val rilasciaMl: () -> Unit = {},
) : ProgettoEsteso {
    override val aggiornamenti: AggiornamentiVista = object : AggiornamentiVista {
        override val cambiamenti: Flow<Cambiamento> =
            merge(r1.aggiornamenti.cambiamenti, aggiornamentiParlanti.cambiamenti)
    }

    /** S3's own scope over [genitore]'s context (its UI dispatcher), a CHILD of [lavoro]: [ferma] joins it. */
    fun scopeSchermata(genitore: CoroutineScope): CoroutineScope =
        CoroutineScope(genitore.coroutineContext + SupervisorJob(lavoro))

    /**
     * AC-420 / ADR 0017 §3 point 6, called by `SessioneProgettoImpl.chiudi` after the session scope was
     * cancelled (which already cancelled [lavoro] and so every pending card command, the S3 Proposta job,
     * `RiallineaTutteLeImpronte` and the realignment loop, interrupting the threads waiting on the native
     * Mutex): waits for them to END, bounded like [CollaboratoriR1.ferma], then stops R1's own workers.
     * [poi] (the database close + `.lock` release) runs only when R2's AND R1's workers all ended —
     * deferred to the last one otherwise (fix-batch-16 MED-1: an extraction inside its native call cannot
     * be interrupted); never a database closed under a live Parlanti worker. Never throws on a timeout.
     * Just before [poi], with every worker ended, [rilasciaMl] releases the print extractor's model (ADR 0019
     * §1.4: it lives as long as the open project); a failing release is logged, never propagated.
     */
    override fun ferma(poi: () -> Unit) {
        val fermato = runBlocking { withTimeoutOrNull(TIMEOUT_ARRESTO_MS) { lavoro.join() } != null }
        if (!fermato) log.warning("lavori dei Parlanti non fermati in tempo: il database attende la loro fine")
        r1.ferma {
            lavoro.invokeOnCompletion {
                rilasciaSilenziosamente()
                poi()
            }
        }
    }

    private fun rilasciaSilenziosamente() {
        try {
            rilasciaMl()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // a native release: any fault, logged
        ) {
            log.log(Level.WARNING, "rilascio del modello delle impronte fallito alla chiusura del progetto", e)
        }
    }

    private companion object {
        const val TIMEOUT_ARRESTO_MS = 5_000L
        val log: Logger = Logger.getLogger(CollaboratoriR2::class.java.name)
    }
}
