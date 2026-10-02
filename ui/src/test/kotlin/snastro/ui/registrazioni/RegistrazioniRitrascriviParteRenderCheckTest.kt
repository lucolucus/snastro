package snastro.ui.registrazioni

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE
import snastro.ui.testi.titoloConfermaRitrascriviParte
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

private val PARTE_2 = RegistrazioneId("parte-2")
private const val TITOLO_LUNGO = "Riunione di coordinamento trimestrale"
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
    espandiIncontro = {},
    modificaOraDiInizio = { _, _ -> },
    modificaNumeroPersoneIncontro = { _, _ -> },
    avviaElaborazioniIncontro = {},
    chiudiErroreIncontro = {},
)

/** AC-I76 render-check: the multi-part 'Ritrascrivi' confirmation, both sizes, light and dark. */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniRitrascriviParteRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun verifica(w: Int, h: Int, scuro: Boolean) = runDesktopComposeUiTest(w, h) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(
                        RigaRegistrazione(
                            registrazioneId = PARTE_2,
                            titolo = "file 2",
                            dataRegistrazione = LocalDate.of(2026, 9, 30),
                            durataMs = 125_000,
                            elaborazione = StatoElaborazioneRiga.Completata,
                            trascrittoDisponibile = true,
                            ritrascriviDisponibile = true,
                            confermaRitrascrivi = true,
                            parte = ParteDiIncontro(2, TITOLO_LUNGO),
                        ),
                    ),
                ),
                azioni = AZIONI,
                scuro = scuro,
                riduciMovimento = true,
            )
        }
        onNodeWithTag("registrazioni-conferma-ritrascrivi-${PARTE_2.valore}", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText(titoloConfermaRitrascriviParte(2, TITOLO_LUNGO)).assertIsDisplayed()
        onNodeWithText(MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE).assertIsDisplayed()
        val png = File(outputDir, "registrazioni-ritrascrivi-parte${if (scuro) "-scuro" else ""}-${w}x$h.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }

    @Test
    fun `AC-I76 dialogo Ritrascrivi della parte a 1280x800`() = verifica(1280, 800, false)

    @Test
    fun `AC-I76 dialogo Ritrascrivi della parte a 1280x800 (scuro)`() = verifica(1280, 800, true)

    @Test
    fun `AC-I76 dialogo Ritrascrivi della parte a 1024x640`() = verifica(1024, 640, false)

    @Test
    fun `AC-I76 dialogo Ritrascrivi della parte a 1024x640 (scuro)`() = verifica(1024, 640, true)
}
