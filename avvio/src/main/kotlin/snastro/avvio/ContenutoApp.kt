package snastro.avvio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import snastro.avvio.progetto.CollaboratoriProgetto
import snastro.avvio.progetto.ContenutoProgetto
import snastro.avvio.progetto.NavigazioneProgetto
import snastro.avvio.progetto.SchermataR1
import snastro.avvio.progetto.ShellProgetto
import snastro.kernel.RegistrazioneId
import snastro.ui.DestinazioneShell
import snastro.ui.ShellUiStato
import snastro.ui.impostazioni.ImpostazioniRoute
import snastro.ui.impostazioni.LocalTemaApp
import snastro.ui.impostazioni.SezioneImpostazioni
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.StatoModelli
import snastro.ui.parlanti.ParlantiRoute
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazioni.RegistrazioniRoute
import snastro.ui.registrazioni.SceltaFileAudio

/** AC-341/AC-177: the shell's sections — Registrazioni and Parlanti (S4). The app's ONE set (ADR 0030 §1). */
internal val SEZIONI_SHELL: Set<DestinazioneShell> = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

/**
 * S5 opens first while the models are not ready (ux-proposal S5 "When: at startup") — it never blocks the app. S5 is
 * the Impostazioni screen's Modelli e licenze section: the caller selects that section when this returns Impostazioni.
 */
internal fun schermataIniziale(servizioModelli: ServizioModelli): SchermataR1 =
    if (servizioModelli.stato.value == StatoModelli.Pronti) SchermataR1.Registrazioni else SchermataR1.Impostazioni

/**
 * The app's ONE content (ADR 0030 §1, AC-C65/AC-C74): S1 without a project (or Impostazioni, full-window, reached
 * from S1's header); with one, S2, S3 of a Registrazione (with the Riassunto tab), S4 (the Parlanti section) and
 * Impostazioni (from the sidebar footer, from any section — S5 is its Modelli e licenze section). The shell, S5,
 * Impostazioni and the ONE per-window [SelezioneSchedaS3] (AC-S121) are remembered for the window; S2 and S4 per
 * project, ABOVE the section switch, so going to Parlanti and back keeps where the user was. `remember` alone gives
 * S1 a FRESH presenter every time its branch is re-entered (Compose disposes a branch's slot table when it leaves) —
 * the same for Impostazioni › Riassunto, so it always re-reads the value the Riassunto tab may have changed.
 * The window follows the theme saved in Impostazioni ([LocalTemaApp]).
 */
@Composable
internal fun ContenutoApp(grafo: Grafo, sceltaCartella: SceltaCartella, sceltaFileAudio: SceltaFileAudio) {
    val shellPresenter = remember { costruisciShellPresenter(grafo) }
    val modelliPresenter = remember { costruisciModelliPresenter(grafo) }
    val impostazioniPresenter = remember { costruisciImpostazioniPresenter(grafo) }
    val selezioneScheda = remember { SelezioneSchedaS3() }
    val preferenze by impostazioniPresenter.preferenzeCorrenti.collectAsState()
    var impostazioniSenzaProgetto by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalTemaApp provides preferenze.tema) {
        ShellProgetto(
            shellPresenter = shellPresenter,
            iniziale = {
                // Also evaluated with NO project open (the shell's navigation is keyed by the project): only an
                // actual project opening on S5 preselects the Modelli section, never S1's own ⚙ entry.
                schermataIniziale(grafo.servizioModelli).also {
                    val conProgetto = shellPresenter.stato.value is ShellUiStato.ConProgetto
                    if (it == SchermataR1.Impostazioni && conProgetto) {
                        impostazioniPresenter.seleziona(SezioneImpostazioni.MODELLI)
                    }
                }
            },
            contenutoSenzaProgetto = {
                if (impostazioniSenzaProgetto) {
                    ImpostazioniRoute(
                        presenter = impostazioniPresenter,
                        sceltaCartella = sceltaCartella,
                        modelli = { ModelliRoute(modelliPresenter) },
                        onIndietro = { impostazioniSenzaProgetto = false },
                    )
                } else {
                    Progetti(grafo, impostazioniPresenter.cartellaProgetti(preferenze), sceltaCartella) {
                        impostazioniSenzaProgetto = true
                    }
                }
            },
            contenuto = { conProgetto, navigazione ->
                val collaboratori = grafo.sessione.collaboratoriCorrenti()
                if (collaboratori != null) {
                    ContenutoConProgetto(
                        grafo = grafo,
                        collaboratori = collaboratori,
                        conProgetto = conProgetto,
                        navigazione = navigazione,
                        selezioneScheda = selezioneScheda,
                        sceltaFileAudio = sceltaFileAudio,
                        impostazioni = {
                            val lunghezza = remember { costruisciLunghezzaRiassuntoPresenter(grafo, collaboratori) }
                            ImpostazioniRoute(
                                presenter = impostazioniPresenter,
                                sceltaCartella = sceltaCartella,
                                modelli = { ModelliRoute(modelliPresenter) },
                                nomeProgetto = conProgetto.progetto.nome,
                                lunghezzaRiassunto = lunghezza,
                            )
                        },
                    )
                }
            },
            etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede,
        )
    }
}

/** S1 on a FRESH presenter each time its branch is entered; [cartella] is the folder new projects go to. */
@Composable
private fun Progetti(grafo: Grafo, cartella: String, sceltaCartella: SceltaCartella, onImpostazioni: () -> Unit) {
    val progettiPresenter = remember { costruisciProgettiPresenter(grafo) }
    ProgettiRoute(progettiPresenter, cartella, sceltaCartella, onImpostazioni)
}

/** The open project's places: S2 and S4 remembered per project, S3 per Registrazione, [impostazioni] as given. */
@Suppress("LongParameterList") // the app graph + the project's collaborators + its shell state + nav + slots
@Composable
private fun ContenutoConProgetto(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    conProgetto: ShellUiStato.ConProgetto,
    navigazione: NavigazioneProgetto,
    selezioneScheda: SelezioneSchedaS3,
    sceltaFileAudio: SceltaFileAudio,
    impostazioni: @Composable () -> Unit,
) {
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
        elenco = { RegistrazioniRoute(registrazioniPresenter, sceltaFileAudio) },
        registrazione = { id ->
            SchermataRegistrazione(grafo, collaboratori, id, selezioneScheda) { navigazione.apriRegistrazione(it) }
        },
        impostazioni = impostazioni,
        parlanti = { ParlantiRoute(parlantiPresenter) },
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
    vaiAllaParte: (RegistrazioneId) -> Unit,
) {
    val scopeS3 = remember(id) { collaboratori.parlanti.scopeSchermata(collaboratori.scope) }
    DisposableEffect(scopeS3) { onDispose { scopeS3.cancel() } }
    val presenter = remember(id) {
        costruisciRegistrazionePresenter(grafo, collaboratori, id, scopeS3, selezioneScheda, vaiAllaParte)
    }
    RegistrazioneRoute(presenter)
}
