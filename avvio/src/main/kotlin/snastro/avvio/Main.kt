package snastro.avvio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import snastro.kernel.Esito
import snastro.ui.DestinazioneShell
import snastro.ui.ShellPresenter
import snastro.ui.ShellRoute
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniRoute
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private const val LARGHEZZA_SMOKE_PX = 1280
private const val ALTEZZA_SMOKE_PX = 800
private const val ATTESA_SMOKE_TIMEOUT_MS = 10_000L
private const val ATTESA_SMOKE_PASSO_MS = 20L

/** AC-350/AC-341: R0's own wiring decision — the shell shows only Registrazioni, never Parlanti. */
internal val SEZIONI_SHELL_R0: Set<DestinazioneShell> = setOf(DestinazioneShell.REGISTRAZIONI)

/**
 * Composition root R0 (release Archivio): manual wiring of the R0 graph ([costruisciGrafoR0]) — S1 ·
 * Progetti, S2 · Registrazioni del Progetto, shell WITHOUT the Parlanti section (AC-341), no S3/S4/S5
 * (AC-350). `run` binding (profile): opens the real window. `--smoke <fixture-dir>` binding (AC-237):
 * opens the fixture project (already containing >=1 imported Registrazione) offscreen, saves one
 * screenshot of S1 and one of S2 to `avvio/build/smoke/`, exits 0 — headless, no sherpa natives, no
 * ML models (R0 registers no Trascrizione/Parlanti/Documento/Modelli service or subscriber at all).
 */
fun main(args: Array<String>) {
    val smokeIndex = args.indexOf("--smoke")
    if (smokeIndex >= 0) {
        val fixtureDir = args.getOrElse(smokeIndex + 1) { "build/smoke-fixture" }
        eseguiSmoke(fixtureDir)
        exitProcess(0)
    }

    val grafo = costruisciGrafoR0()
    application {
        Window(onCloseRequest = ::exitApplication, title = "snastro") {
            ContenutoApp(grafo)
        }
    }
}

/**
 * R0's shell content: S1 when no Progetto is open, S2 for the open Progetto's Registrazioni.
 * `remember` alone (no explicit key) already gives a FRESH presenter every time a `when` branch of
 * [snastro.ui.SchermataShell] is re-entered (Compose disposes a branch's slot table when it leaves
 * composition) — this is what keeps S1's list fresh after creating/closing a Progetto, and S2 bound
 * to the Progetto currently open.
 */
@Composable
internal fun ContenutoApp(grafo: GrafoR0) {
    val shellPresenter = remember {
        ShellPresenter(grafo.scope, grafo.io, grafo.sessione, SEZIONI_SHELL_R0)
    }
    ShellRoute(
        presenter = shellPresenter,
        contenutoSenzaProgetto = {
            val progettiPresenter = remember {
                ProgettiPresenter(grafo.scope, grafo.io, grafo.elencoProgetti, grafo.sessione)
            }
            ProgettiRoute(progettiPresenter)
        },
        contenuto = { conProgetto ->
            val collaboratori = grafo.sessione.collaboratoriCorrenti()
            if (collaboratori != null) {
                val registrazioniPresenter = remember(conProgetto.progetto.progettoId) {
                    costruisciRegistrazioniPresenter(grafo, collaboratori)
                }
                RegistrazioniRoute(registrazioniPresenter)
            }
        },
    )
}

/**
 * AC-350: R0 wires `statiElaborazione`/`avviaElaborazione` as `null` (default) — no Trascrizione
 * source at all. H2: launched on [CollaboratoriProgettoAperto.scope] — this Progetto's OWN child
 * scope, never the app-wide [GrafoR0.scope] — so [snastro.ui.registrazioni.RegistrazioniPresenter]'s
 * endless collectors (`init`) are cancelled together when [SessioneProgettoImpl.chiudi] cancels that
 * scope, instead of outliving the closed Progetto.
 */
internal fun costruisciRegistrazioniPresenter(
    grafo: GrafoR0,
    collaboratori: CollaboratoriProgettoAperto,
): RegistrazioniPresenter = RegistrazioniPresenter(
    scope = collaboratori.scope,
    io = grafo.io,
    registrazioni = collaboratori.registrazioni,
    aggiungiRegistrazione = collaboratori.aggiungiRegistrazione,
    modificaDataRegistrazione = collaboratori.modificaDataRegistrazione,
    rinominaRegistrazione = collaboratori.rinominaRegistrazione,
    lettore = collaboratori.lettoreAudio,
    aggiornamenti = collaboratori.aggiornamentiVista,
    clock = grafo.clock,
)

@OptIn(ExperimentalTestApi::class)
internal fun eseguiSmoke(fixtureDir: String) {
    // Un registro ISOLATO, mai quello reale per-utente (AC-348): uno smoke non deve ne' inquinare
    // ne' collidere con l'elenco dei progetti recenti dello sviluppatore che lo esegue.
    val cartellaRegistroSmoke = Files.createTempDirectory("snastro-smoke-registro")
    val grafo = costruisciGrafoR0(cartellaRegistroSmoke)
    val outputDir = File("build/smoke").apply { mkdirs() }

    runDesktopComposeUiTest(LARGHEZZA_SMOKE_PX, ALTEZZA_SMOKE_PX) {
        setContent { ContenutoApp(grafo) }

        attendi { esisteTag("progetti-lista") || esisteTag("progetti-vuoto") }
        salvaSchermata(outputDir, "s1")

        val esito = grafo.sessione.apri(fixtureDir)
        check(esito is Esito.Ok) { "smoke: impossibile aprire il progetto fixture '$fixtureDir': $esito" }

        attendi { esisteTag("registrazioni-lista") }
        salvaSchermata(outputDir, "s2")
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.esisteTag(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.attendi(condizione: () -> Boolean) {
    val scadenza = System.currentTimeMillis() + ATTESA_SMOKE_TIMEOUT_MS
    while (System.currentTimeMillis() < scadenza) {
        waitForIdle()
        if (condizione()) return
        Thread.sleep(ATTESA_SMOKE_PASSO_MS)
    }
    check(condizione()) { "smoke: condizione non soddisfatta entro ${ATTESA_SMOKE_TIMEOUT_MS}ms" }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.salvaSchermata(cartella: File, nome: String) {
    val bitmap = onRoot().captureToImage().toAwtImage()
    ImageIO.write(bitmap, "PNG", File(cartella, "$nome.png"))
}
