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
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO
import snastro.ui.testi.titoloConfermaEliminaParte
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

/** `dialogo-elimina-parte` render-check (AC-I72): the non-last Parte confirmation, both sizes, light and dark. */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniEliminaParteRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun verifica(w: Int, h: Int, scuro: Boolean, trascritto: Boolean) = runDesktopComposeUiTest(w, h) {
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
                            trascrittoDisponibile = trascritto,
                            confermaElimina = true,
                            parte = ParteDiIncontro(2, TITOLO_LUNGO),
                        ),
                    ),
                ),
                azioni = AZIONI,
                scuro = scuro,
                riduciMovimento = true,
            )
        }
        onNodeWithTag("registrazioni-conferma-elimina-${PARTE_2.valore}", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(titoloConfermaEliminaParte(2, TITOLO_LUNGO)).assertIsDisplayed()
        if (trascritto) {
            onNodeWithText(MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO).assertIsDisplayed()
        } else {
            onNodeWithText(MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO).assertIsDisplayed()
        }
        onNodeWithText(ETICHETTA_CONFERMA_ELIMINAZIONE).assertIsDisplayed()
        onNodeWithText(ETICHETTA_ANNULLA).assertIsDisplayed()
        val variante = if (trascritto) "con" else "senza"
        val nome = "registrazioni-elimina-parte-$variante-trascritto"
        val png = File(outputDir, "$nome${if (scuro) "-scuro" else ""}-${w}x$h.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }

    @Test
    fun `AC-I72 parte non ultima con trascritto a 1280x800`() = verifica(1280, 800, false, true)

    @Test
    fun `AC-I72 parte non ultima con trascritto a 1280x800 (scuro)`() = verifica(1280, 800, true, true)

    @Test
    fun `AC-I72 parte non ultima con trascritto a 1024x640`() = verifica(1024, 640, false, true)

    @Test
    fun `AC-I72 parte non ultima con trascritto a 1024x640 (scuro)`() = verifica(1024, 640, true, true)

    @Test
    fun `AC-I72 parte non ultima senza trascritto a 1280x800`() = verifica(1280, 800, false, false)

    @Test
    fun `AC-I72 parte non ultima senza trascritto a 1024x640 (scuro)`() = verifica(1024, 640, true, false)
}
