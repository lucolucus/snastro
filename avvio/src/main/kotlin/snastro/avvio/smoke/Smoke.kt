package snastro.avvio.smoke

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
import snastro.avvio.ContenutoApp
import snastro.avvio.Grafo
import snastro.avvio.costruisciGrafo
import snastro.avvio.trascrizione.SceltaMl
import snastro.avvio.trascrizione.SelezioneAdattatoriMl
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.DestinazioneShell
import snastro.ui.modelli.StatoModelli
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazioni.SceltaFileAudio
import snastro.ui.testi.ETICHETTA_SCARICA
import snastro.ui.testi.etichetta
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaModelliMancanti
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private const val LARGHEZZA_SMOKE_PX = 1280
private const val ALTEZZA_SMOKE_PX = 800
private const val ATTESA_SMOKE_TIMEOUT_MS = 10_000L
private const val ATTESA_SMOKE_PASSO_MS = 20L
private const val VOCI = 4
private const val DA_NOMINARE = 3

/**
 * AC-237 + AC-351 + AC-357 + AC-S151 + AC-I91: S1, S2 over the fixture's 2-Parti Incontro (collapsed with its
 * identification badge, then expanded), then S3 of each Parte with its Voci panel — Parte 1 reached through S2's own
 * sub-row click, the real wiring, Parte 2 through the Parte switcher — and with the Riassunto tab selected (the
 * fixture's pronto Riassunto of the Incontro), then S4 through the shell's Parlanti section,
 * then S5 through the sidebar footer, over the REAL model catalogue on
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
    // fix-batch-16 LOW-1: S5 over the REAL catalogue on the empty throwaway cache ('Mancanti', 'Scarica') —
    // building it loads and downloads nothing; the pipeline and the print extractor stay on the Finte.
    // AC-C75: the app's ONE ProvisioningModelli (ModelliApp) feeds S5, the Riassunto tab and
    // DisponibilitaModelloLinguistico alike — the smoke only swaps its catalogue, never adds a second one.
    val grafo = grafoSmoke(cartellaRegistroSmoke, cartellaModelliSmoke)
    val modelliReali = grafo.servizioModelli
    val outputDir = File("build/smoke").apply { mkdirs() }

    try {
        runDesktopComposeUiTest(LARGHEZZA_SMOKE_PX, ALTEZZA_SMOKE_PX) {
            // The smoke script never exercises S1's folder pickers (it opens the fixture project
            // directly through `sessione.apri`, below) — a `SceltaCartella` that always "cancels" is
            // enough; a real `java.awt.FileDialog` has no owner window in this OFFSCREEN test harness.
            setContent { ContenutoApp(grafo, SceltaCartella { null }, SceltaFileAudio { emptyList() }) }

            attendi { esisteTag("progetti-lista") || esisteTag("progetti-vuoto") }
            salvaSchermata(outputDir, "s1")

            // Impostazioni, full-window from S1's header, and back.
            onNodeWithTag("progetti-impostazioni").performClick()
            attendi { esisteTag("impostazioni-tema") }
            salvaSchermata(outputDir, "impostazioni")
            onNodeWithTag("impostazioni-indietro").performClick()
            attendi { esisteTag("progetti-lista") || esisteTag("progetti-vuoto") }

            val esito = grafo.sessione.apri(fixtureDir)
            check(esito is Esito.Ok) { "smoke: impossibile aprire il progetto fixture '$fixtureDir': $esito" }

            // Models missing (the real catalogue on an empty cache): the project opens on S5 first, the
            // onboarding path — S2 is one click on the sidebar's own (already-selected) 'Registrazioni'
            // item away (rework cycle 1, HIGH #9: navigation is the sidebar, not a standalone top bar).
            attendi { esisteTag("shell-nav-registrazioni") }
            onAllNodesWithText(etichetta(DestinazioneShell.REGISTRAZIONI))[0].performClick()
            percorriIncontro(grafo, fixtureDir, outputDir)

            onAllNodesWithText(etichetta(DestinazioneShell.PARLANTI))[0].performClick()
            attendi { esisteTag("parlanti-lista") }
            salvaSchermata(outputDir, "s4")

            // Rework cycle 2 (HIGH #1): Impostazioni (S5 is its 'Modelli e licenze' section) is reached from the
            // sidebar's own footer row from ANY section — straight from Parlanti here.
            onNodeWithTag("shell-piede").performClick()
            onNodeWithTag("impostazioni-sezione-modelli").performClick()
            // AC-556: `licenze()` now also lists the bundled fonts, so it is no longer the model count —
            // the actual missing-models number comes from `StatoModelli.Mancanti` itself.
            val numeroModelliMancanti = (modelliReali.stato.value as StatoModelli.Mancanti).numero
            attendi {
                esisteTag("modelli-mancanti") &&
                    esisteTesto(etichettaModelliMancanti(numeroModelliMancanti)) &&
                    esisteTesto(ETICHETTA_SCARICA)
            }
            salvaSchermata(outputDir, "s5")
        }
    } finally {
        grafo.sessione.chiudi() // ferma la coda, la rigenerazione e i lavori dei Parlanti, rilascia il .lock
    }
}

/**
 * AC-I91: S2 over the fixture's 2-Parti Incontro (collapsed with its identification badge over both Parti, then
 * expanded), S3 of Parte 1 through its sub-row, S3 of Parte 2 through the switcher, and the Riassunto tab.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.percorriIncontro(grafo: Grafo, fixtureDir: String, outputDir: File) {
    val incontro = checkNotNull(grafo.sessione.collaboratoriCorrenti()?.incontri()?.singleOrNull()) {
        "smoke: il progetto fixture '$fixtureDir' deve avere un solo Incontro"
    }
    check(incontro.parti.size >= 2) { "smoke: l'Incontro del fixture ha ${incontro.parti.size} Parti, ne servono 2" }
    val idIncontro = incontro.incontroId.valore
    val parte1 = incontro.parti[0].registrazioneId
    check(grafo.primaRegistrazioneCompletata() != null) {
        "smoke: il progetto fixture '$fixtureDir' non ha alcuna Registrazione con un Trascritto completato"
    }
    // The fixture has 4 Voci, 1 of them named (AC-357 over the Incontro).
    attendi {
        esisteTag("registrazioni-incontro-$idIncontro") && esisteTesto(etichettaIdentificazione(VOCI, DA_NOMINARE))
    }
    salvaSchermata(outputDir, "s2")

    onNodeWithTag("registrazioni-incontro-chevron-$idIncontro").performClick()
    attendi { esisteTag("registrazioni-incontro-parti-$idIncontro") }
    salvaSchermata(outputDir, "s2-espansa")

    onNodeWithTag("registrazioni-riga-${parte1.valore}").performSemanticsAction(SemanticsActions.OnClick)
    // AC-402/AC-405: the Voci panel, Voce 1 named after its Parlante, Voce 2 still to identify.
    attendi {
        esisteTag("registrazione-lista") && esisteTag("voci-pannello") && esisteTag("voce-1-nome") &&
            esisteTag("registrazione-parti")
    }
    salvaSchermata(outputDir, "s3-parte-1")

    onNodeWithTag("parte-1").performClick()
    attendi { esisteTesto("Parte 2 di 2", sottostringa = true) && esisteTag("voci-pannello") }
    salvaSchermata(outputDir, "s3-parte-2")

    // AC-S151/AC-I91: the Riassunto tab selected, the fixture's pronto Riassunto of the Incontro shown.
    onNodeWithTag("scheda-1").performClick()
    attendi { esisteTag("riassunto-contenuto") }
    salvaSchermata(outputDir, "s3-riassunto")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.esisteTag(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.esisteTesto(testo: String, sottostringa: Boolean = false): Boolean =
    onAllNodesWithText(testo, substring = sottostringa).fetchSemanticsNodes().isNotEmpty()

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

/**
 * The `--smoke` graph: the app's own ([costruisciGrafo]) on the ML Finte ([SceltaMl.FINTE]) over the REAL model
 * catalogue — throwaway [cartellaRegistro] and [cartellaModelli]; ONE `ProvisioningModelli` behind S5, the Riassunto
 * tab and `DisponibilitaModelloLinguistico` alike (AC-C75).
 */
internal fun grafoSmoke(cartellaRegistro: Path, cartellaModelli: Path): Grafo = costruisciGrafo(
    cartellaRegistro,
    SceltaMl.FINTE,
    cartellaModelli,
    catalogo = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI),
)

/** The open project's first Registrazione whose Elaborazione is COMPLETATA, or `null` (the `--smoke` S3 target). */
internal fun Grafo.primaRegistrazioneCompletata(): RegistrazioneId? {
    val collaboratori = sessione.collaboratoriCorrenti() ?: return null
    val ids = collaboratori.registrazioni().map { it.registrazioneId }
    return collaboratori.trascrizione.statiElaborazione(ids)
        .firstOrNull { it.stato == StatoElaborazioneVista.COMPLETATA }
        ?.registrazioneId
}
