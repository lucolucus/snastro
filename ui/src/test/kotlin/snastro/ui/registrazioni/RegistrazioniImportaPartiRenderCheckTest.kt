package snastro.ui.registrazioni

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.ui.testi.ETICHETTA_PARTI_AGGIUNTE
import snastro.ui.testi.messaggioPartiAggiunte
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals

private val DIMENSIONI = listOf(1280 to 800, 1024 to 640)
private val TRE_FILE = listOf("/sorgenti/a.m4a", "/sorgenti/b.m4a", "/sorgenti/c.m4a")
private val MOLTI_FILE_LUNGHI = (1..12).map {
    "/sorgenti/Riunione-di-coordinamento-trimestrale-con-tutto-il-gruppo-di-progetto-parte-$it-versione-definitiva.m4a"
}

/** `:ui:renderCheck` for AC-I70/AC-I71: the import dialog (default, long list, error, sending) and the notice. */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniImportaPartiRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun render(nome: String, dialogo: DialogoImporta, verifica: ComposeUiTest.() -> Unit = {}) {
        for ((w, h) in DIMENSIONI) {
            for (scuro in listOf(false, true)) {
                runDesktopComposeUiTest(w, h) {
                    setContent {
                        SchermataRegistrazioni(
                            stato = RegistrazioniUiStato.Dati(righe = emptyList(), dialogoImporta = dialogo),
                            azioni = AZIONI,
                            scuro = scuro,
                            riduciMovimento = true,
                        )
                    }
                    onNodeWithTag("registrazioni-dialogo-importa").assertIsDisplayed()
                    onNodeWithTag("registrazioni-importa-conferma").assertIsDisplayed()
                    onNodeWithTag("registrazioni-importa-annulla").assertIsDisplayed()
                    verifica()
                    cattura(nome, w, h, scuro)
                }
            }
        }
    }

    @Test
    fun `AC-I70 il dialogo di 3 file mostra titolo, elenco, scelta preselezionata e bottoni`() =
        render("importa-parti-dialogo", DialogoImporta(TRE_FILE)) {
            onNodeWithText("Importare 3 file").assertIsDisplayed()
            onNodeWithText("1. a.m4a").assertIsDisplayed()
            onNodeWithText("3. c.m4a").assertIsDisplayed()
            onNodeWithTag("registrazioni-importa-un-incontro").assertIsSelected()
            onNodeWithText("3 incontri separati").assertIsDisplayed()
        }

    @Test
    fun `AC-I70 un elenco lungo di nomi lunghi resta nel dialogo e i bottoni restano visibili`() =
        render("importa-parti-dialogo-lungo", DialogoImporta(MOLTI_FILE_LUNGHI)) {
            onNodeWithText("Importare 12 file").assertIsDisplayed()
        }

    @Test
    fun `AC-I71 l errore tutto o niente appare nel dialogo`() =
        render(
            "importa-parti-dialogo-errore",
            DialogoImporta(TRE_FILE, errore = "Nessun file importato: «b.m4a» non è leggibile."),
        ) {
            onNodeWithTag("registrazioni-dialogo-importa-errore").assertIsDisplayed()
            onNodeWithText("Nessun file importato: «b.m4a» non è leggibile.").assertIsDisplayed()
        }

    @Test
    fun `AC-I70 durante l invio i bottoni sono disabilitati`() =
        render("importa-parti-dialogo-invio", DialogoImporta(TRE_FILE, invioInCorso = true)) {
            onNodeWithTag("registrazioni-importa-conferma").assertIsNotEnabled()
            onNodeWithTag("registrazioni-importa-annulla").assertIsNotEnabled()
        }

    @Test
    fun `AC-I70 le scelte e i bottoni inoltrano le azioni`() = runDesktopComposeUiTest(1280, 800) {
        val eventi = mutableListOf<String>()
        val azioni = AZIONI.copy(
            scegliImporta = { eventi += "scelta:$it" },
            confermaImporta = { eventi += "conferma" },
            annullaImporta = { eventi += "annulla" },
        )
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(righe = emptyList(), dialogoImporta = DialogoImporta(TRE_FILE)),
                azioni = azioni,
                scuro = false,
                riduciMovimento = true,
            )
        }
        onNodeWithTag("registrazioni-importa-separati").assertIsEnabled().performClick()
        onNodeWithTag("registrazioni-importa-conferma").performClick()
        onNodeWithTag("registrazioni-importa-annulla").performClick()
        assertEquals(listOf("scelta:IncontriSeparati", "conferma", "annulla"), eventi)
    }

    @Test
    fun `AC-I71 l avviso Parti aggiunte e visibile e chiudibile`() {
        for ((w, h) in DIMENSIONI) {
            runDesktopComposeUiTest(w, h) {
                setContent {
                    SchermataRegistrazioni(
                        stato = RegistrazioniUiStato.Dati(
                            righe = emptyList(),
                            avviso = messaggioPartiAggiunte(2, "Riunione"),
                            titoloAvviso = ETICHETTA_PARTI_AGGIUNTE,
                        ),
                        azioni = AZIONI,
                        scuro = false,
                        riduciMovimento = true,
                    )
                }
                onNodeWithTag("registrazioni-avviso").assertIsDisplayed()
                onNodeWithText("2 parti aggiunte a «Riunione».").assertIsDisplayed()
                onNodeWithText(ETICHETTA_PARTI_AGGIUNTE).assertIsDisplayed()
                cattura("importa-parti-avviso", w, h, false)
            }
        }
    }

    private fun ComposeUiTest.cattura(nome: String, width: Int, height: Int, scuro: Boolean) {
        val png = File(outputDir, "$nome${if (scuro) "-scuro" else ""}-${width}x$height.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}

private val AZIONI = AzioniRegistrazioni(
    importa = {},
    modificaData = { _, _ -> },
    rinomina = { _, _ -> },
    riproduci = {},
    pausa = {},
    avviaElaborazione = {},
    modificaNumeroPersone = { _, _ -> },
    apriRiga = {},
    chiudiErrore = {},
    chiudiErroreRiga = {},
    riprova = {},
    ritrascrivi = {},
    annullaRitrascrivi = {},
    confermaRitrascrivi = {},
    annullaElaborazione = {},
    elimina = {},
    annullaElimina = {},
    confermaElimina = {},
    chiudiAvviso = {},
    aggiungiParti = { _, _, _ -> },
    scegliImporta = {},
    confermaImporta = {},
    annullaImporta = {},
)
