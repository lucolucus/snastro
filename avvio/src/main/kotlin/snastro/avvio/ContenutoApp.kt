package snastro.avvio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import snastro.avvio.progetto.CollaboratoriProgetto
import snastro.avvio.progetto.ContenutoProgetto
import snastro.avvio.progetto.SchermataR1
import snastro.avvio.progetto.ShellProgetto
import snastro.kernel.RegistrazioneId
import snastro.ui.DestinazioneShell
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.StatoModelli
import snastro.ui.parlanti.ParlantiRoute
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazioni.RegistrazioniRoute

/** AC-341/AC-177: the shell's sections — Registrazioni and Parlanti (S4). The app's ONE set (ADR 0030 §1). */
internal val SEZIONI_SHELL: Set<DestinazioneShell> = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

/** S5 opens first while the models are not ready (ux-proposal S5 "When: at startup") — it never blocks the app. */
internal fun schermataIniziale(servizioModelli: ServizioModelli): SchermataR1 =
    if (servizioModelli.stato.value == StatoModelli.Pronti) SchermataR1.Registrazioni else SchermataR1.Modelli

/**
 * The app's ONE content (ADR 0030 §1, AC-C65/AC-C74): S1 without a project; with one, S2, S3 of a Registrazione
 * (with the Riassunto tab), S4 (the Parlanti section) and S5 (from the sidebar footer, from any section). The shell,
 * S5 and the ONE per-window [SelezioneSchedaS3] (AC-S121) are remembered for the window; S2 and S4 per project, ABOVE
 * the section switch, so going to Parlanti and back keeps where the user was. `remember` alone gives S1 a FRESH
 * presenter every time its branch is re-entered (Compose disposes a branch's slot table when it leaves).
 */
@Composable
internal fun ContenutoApp(grafo: Grafo, sceltaCartella: SceltaCartella) {
    val shellPresenter = remember { costruisciShellPresenter(grafo) }
    val modelliPresenter = remember { costruisciModelliPresenter(grafo) }
    val selezioneScheda = remember { SelezioneSchedaS3() }
    ShellProgetto(
        shellPresenter = shellPresenter,
        iniziale = { schermataIniziale(grafo.servizioModelli) },
        contenutoSenzaProgetto = {
            val progettiPresenter = remember { costruisciProgettiPresenter(grafo) }
            ProgettiRoute(progettiPresenter, grafo.cartellaProgettiPredefinita, sceltaCartella)
        },
        contenuto = { conProgetto, navigazione ->
            val collaboratori = grafo.sessione.collaboratoriCorrenti()
            if (collaboratori != null) {
                val progettoId = conProgetto.progetto.progettoId
                val registrazioniPresenter = remember(progettoId) {
                    costruisciRegistrazioniPresenter(grafo, collaboratori) { id -> navigazione.apriRegistrazione(id) }
                }
                val parlantiPresenter = remember(progettoId) { costruisciParlantiPresenter(grafo, collaboratori) }
                // AC-632 step (4): a deleted Registrazione's S3 place is forgotten, on the UI dispatcher.
                DisposableEffect(collaboratori, navigazione) {
                    collaboratori.pulizia.dimenticaPosto = { id ->
                        collaboratori.scope.launch { navigazione.dimentica(id) }
                    }
                    onDispose { collaboratori.pulizia.dimenticaPosto = {} }
                }
                ContenutoProgetto(
                    conProgetto = conProgetto,
                    navigazione = navigazione,
                    elenco = { RegistrazioniRoute(registrazioniPresenter) },
                    registrazione = { id -> SchermataRegistrazione(grafo, collaboratori, id, selezioneScheda) },
                    modelli = { ModelliRoute(modelliPresenter) },
                    parlanti = { ParlantiRoute(parlantiPresenter) },
                )
            }
        },
        etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede,
    )
}

/**
 * S3 of [id] on its OWN screen scope inside the Parlanti per-project scope, cancelled when S3 leaves composition or
 * shows another Registrazione: its ONE Proposta job goes with it (AC-421), while a pending card command lives on in the
 * project scope (AC-418); closing the project cancels and JOINS it before the database closes (AC-420). The
 * presenter (and its Riassunto tab, hoisted here: switching Trascrizione ↔ Riassunto keeps its state) is keyed by [id].
 */
@Composable
private fun SchermataRegistrazione(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    id: RegistrazioneId,
    selezioneScheda: SelezioneSchedaS3,
) {
    val scopeS3 = remember(id) { collaboratori.parlanti.scopeSchermata(collaboratori.scope) }
    DisposableEffect(scopeS3) { onDispose { scopeS3.cancel() } }
    val presenter = remember(id) {
        costruisciRegistrazionePresenter(grafo, collaboratori, id, scopeS3, selezioneScheda)
    }
    RegistrazioneRoute(presenter)
}
