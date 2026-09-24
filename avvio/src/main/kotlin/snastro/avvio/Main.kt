package snastro.avvio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.servizioModelliReali
import snastro.avvio.r2.ContenutoAppR2
import snastro.avvio.r2.GrafoR2
import snastro.avvio.r2.costruisciGrafoR2
import snastro.avvio.r2.primaRegistrazioneCompletata
import snastro.kernel.Esito
import snastro.ui.DestinazioneShell
import snastro.ui.ShellPresenter
import snastro.ui.ShellRoute
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniRoute
import snastro.ui.testi.ETICHETTA_SCARICA
import snastro.ui.testi.etichetta
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaModelliMancanti
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
 * Composition root R2 (release Parlanti, avvio-parlanti): the R0 graph ([costruisciGrafoR0]) EXTENDED by
 * R1's, itself EXTENDED by R2's ([costruisciGrafoR2]) — S1, S2 with the Trascrizione sources and the
 * identification badge, S3 with the Voci panel and the Revisione UI, S5, and the shell's Parlanti section
 * (S4). `run` binding (profile): opens the real window. `--smoke <fixture-dir>` binding (AC-237, AC-351,
 * AC-357): opens the fixture project offscreen and saves S1, S2 (badge), S3 (Voci panel), S4 and S5 to
 * `avvio/build/smoke/`, exits 0 — headless, on the ML Finte ([SceltaMl.FINTE]): no natives, no models.
 *
 * R0 alone ([ContenutoApp] over [costruisciGrafoR0] without extension) and R1 (`ContenutoAppR1` over
 * `costruisciGrafoR1`) are kept as they are: they are what the R0/R1-mode tests prove still behaves as
 * released (AC-350, AC-355/AC-356).
 */
fun main(args: Array<String>) {
    val smokeIndex = args.indexOf("--smoke")
    if (smokeIndex >= 0) {
        val fixtureDir = args.getOrElse(smokeIndex + 1) { "build/smoke-fixture" }
        eseguiSmoke(fixtureDir)
        exitProcess(0)
    }

    val grafo = costruisciGrafoR2()
    application {
        val esci = {
            chiudiPrimaDiUscire(grafo.r0.sessione::chiudi) // LOW-3: bounded, never a hung exit
            exitApplication()
        }
        Window(onCloseRequest = esci, title = "snastro") {
            ContenutoAppR2(grafo)
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
            ProgettiRoute(progettiPresenter, grafo.cartellaProgettiPredefinita)
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

/**
 * AC-237 + AC-351 + AC-357: S1, S2 (with the identification badge), then S3 of the fixture's first
 * COMPLETATA Registrazione with its Voci panel — reached through S2's own row click, the real wiring —
 * then S4 through the shell's Parlanti section, then S5 through the bar, over the REAL model catalogue on
 * the empty isolated cache (fix-batch-16 LOW-1: the entries missing, 'Scarica'). Isolated registry and
 * model cache, pipeline and print extractor on the ML Finte: nothing of the developer's own machine is
 * read or written, no native is loaded, nothing is downloaded.
 */
@OptIn(ExperimentalTestApi::class)
internal fun eseguiSmoke(fixtureDir: String) {
    // Un registro ISOLATO, mai quello reale per-utente (AC-348): uno smoke non deve ne' inquinare
    // ne' collidere con l'elenco dei progetti recenti dello sviluppatore che lo esegue.
    val cartellaRegistroSmoke = Files.createTempDirectory("snastro-smoke-registro")
    val cartellaModelliSmoke = Files.createTempDirectory("snastro-smoke-modelli")
    val grafoFinte = costruisciGrafoR2(cartellaRegistroSmoke, SceltaMl.FINTE, cartellaModelliSmoke)
    // fix-batch-16 LOW-1: S5 over the REAL catalogue on the empty throwaway cache ('Mancanti', 'Scarica') —
    // building it loads and downloads nothing; the pipeline and the print extractor stay on the Finte.
    val modelliReali = servizioModelliReali(cartellaModelliSmoke)
    val grafo = GrafoR2(grafoFinte.r0, modelliReali, grafoFinte.apriEsterno)
    val outputDir = File("build/smoke").apply { mkdirs() }

    try {
        runDesktopComposeUiTest(LARGHEZZA_SMOKE_PX, ALTEZZA_SMOKE_PX) {
            setContent { ContenutoAppR2(grafo) }

            attendi { esisteTag("progetti-lista") || esisteTag("progetti-vuoto") }
            salvaSchermata(outputDir, "s1")

            val esito = grafo.r0.sessione.apri(fixtureDir)
            check(esito is Esito.Ok) { "smoke: impossibile aprire il progetto fixture '$fixtureDir': $esito" }

            // Models missing (the real catalogue on an empty cache): the project opens on S5 first, the
            // onboarding path — S2 is one click on the bar away.
            attendi { esisteTag("avvio-nav-registrazioni") }
            onNodeWithTag("avvio-nav-registrazioni").performClick()
            val completata = checkNotNull(grafo.primaRegistrazioneCompletata()) {
                "smoke: il progetto fixture '$fixtureDir' non ha alcuna Registrazione con un Trascritto completato"
            }
            // AC-357: the badge — the fixture's Trascritto has 2 Voci, 1 of them named.
            attendi { esisteTag("registrazioni-lista") && esisteTesto(etichettaIdentificazione(2, 1)) }
            salvaSchermata(outputDir, "s2")

            onNodeWithTag("registrazioni-riga-${completata.valore}").performSemanticsAction(SemanticsActions.OnClick)
            // AC-402/AC-405: the Voci panel, Voce 1 named after its Parlante, Voce 2 still to identify.
            attendi { esisteTag("registrazione-lista") && esisteTag("voci-pannello") && esisteTag("voce-1-nome") }
            salvaSchermata(outputDir, "s3")

            onAllNodesWithText(etichetta(DestinazioneShell.PARLANTI))[0].performClick()
            attendi { esisteTag("parlanti-lista") }
            salvaSchermata(outputDir, "s4")

            onAllNodesWithText(etichetta(DestinazioneShell.REGISTRAZIONI))[0].performClick()
            attendi { esisteTag("avvio-nav-modelli") }
            onNodeWithTag("avvio-nav-modelli").performClick()
            attendi {
                esisteTag("modelli-mancanti") &&
                    esisteTesto(etichettaModelliMancanti(modelliReali.licenze().size)) &&
                    esisteTesto(ETICHETTA_SCARICA)
            }
            salvaSchermata(outputDir, "s5")
        }
    } finally {
        grafo.r0.sessione.chiudi() // ferma la coda, la rigenerazione e i lavori dei Parlanti, rilascia il .lock
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.esisteTag(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.esisteTesto(testo: String): Boolean =
    onAllNodesWithText(testo).fetchSemanticsNodes().isNotEmpty()

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
