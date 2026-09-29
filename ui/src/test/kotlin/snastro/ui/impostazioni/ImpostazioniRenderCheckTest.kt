package snastro.ui.impostazioni

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import snastro.ui.progetti.SceltaCartellaFinta
import snastro.ui.testi.MESSAGGIO_RIASSUNTO_SENZA_PROGETTO
import snastro.ui.testi.TITOLO_ASPETTO
import snastro.ui.testi.TITOLO_LUNGHEZZA_RIASSUNTO
import java.io.File
import java.util.stream.Stream
import javax.imageio.ImageIO

private val AZIONI = AzioniImpostazioni(
    seleziona = {},
    cambiaTema = {},
    cambiaCartellaProgetti = {},
    ripristinaCartellaProgetti = {},
    chiudiErrore = {},
)
private val AZIONI_RIASSUNTO = AzioniLunghezzaRiassunto(cambia = {}, salva = {}, ripristina = {})
private val STATO = ImpostazioniUiStato(
    tema = TemaApp.SISTEMA,
    cartellaProgetti = "/Users/io/Documents/snastro",
)
private const val MODELLI_FINTI = "Contenuto di Modelli e licenze"

/** `:ui:renderCheck`: every Impostazioni section, both sizes, both themes (sizing/overflow/contrast). */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class ImpostazioniRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @ParameterizedTest
    @MethodSource("dimensioni")
    fun `Generali mostra tema e cartella dei nuovi progetti`(w: Int, h: Int, scuro: Boolean) =
        runDesktopComposeUiTest(w, h) {
            setContent {
                SchermataImpostazioni(
                    stato = STATO.copy(cartellaPersonalizzata = true),
                    azioni = AZIONI,
                    riassunto = null,
                    sceltaCartella = SceltaCartellaFinta(),
                    modelli = { Text(MODELLI_FINTI) },
                    onIndietro = {},
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(TITOLO_ASPETTO).assertIsDisplayed()
            onNodeWithTag("impostazioni-percorso-cartella").assertIsDisplayed()
            onNodeWithTag("impostazioni-ripristina-cartella").assertIsDisplayed()
            onNodeWithTag("impostazioni-indietro").assertIsDisplayed()
            catturaPng("impostazioni-generali", w, h, scuro)
        }

    @ParameterizedTest
    @MethodSource("dimensioni")
    fun `Riassunto con un progetto aperto mostra l editor`(w: Int, h: Int, scuro: Boolean) =
        runDesktopComposeUiTest(w, h) {
            setContent {
                SchermataImpostazioni(
                    stato = STATO.copy(sezione = SezioneImpostazioni.RIASSUNTO),
                    azioni = AZIONI,
                    riassunto = RiassuntoImpostazioni(
                        nomeProgetto = "Consiglio comunale",
                        stato = LunghezzaRiassuntoUiStato.Dati(
                            testo = "1500",
                            salvata = 1500,
                            minimo = 300,
                            massimo = 2500,
                            conferma = true,
                        ),
                        azioni = AZIONI_RIASSUNTO,
                    ),
                    sceltaCartella = SceltaCartellaFinta(),
                    modelli = { Text(MODELLI_FINTI) },
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(TITOLO_LUNGHEZZA_RIASSUNTO).assertIsDisplayed()
            onNodeWithTag("impostazioni-campo-parole").assertIsDisplayed()
            onNodeWithTag("impostazioni-parole-salvata").assertIsDisplayed()
            catturaPng("impostazioni-riassunto", w, h, scuro)
        }

    @ParameterizedTest
    @MethodSource("dimensioni")
    fun `Riassunto senza progetto spiega perche`(w: Int, h: Int, scuro: Boolean) =
        runDesktopComposeUiTest(w, h) {
            setContent {
                SchermataImpostazioni(
                    stato = STATO.copy(sezione = SezioneImpostazioni.RIASSUNTO),
                    azioni = AZIONI,
                    riassunto = null,
                    sceltaCartella = SceltaCartellaFinta(),
                    modelli = { Text(MODELLI_FINTI) },
                    onIndietro = {},
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(MESSAGGIO_RIASSUNTO_SENZA_PROGETTO).assertIsDisplayed()
            catturaPng("impostazioni-riassunto-senza-progetto", w, h, scuro)
        }

    @ParameterizedTest
    @MethodSource("dimensioni")
    fun `Modelli e licenze ospita S5`(w: Int, h: Int, scuro: Boolean) = runDesktopComposeUiTest(w, h) {
        setContent {
            SchermataImpostazioni(
                stato = STATO.copy(sezione = SezioneImpostazioni.MODELLI),
                azioni = AZIONI,
                riassunto = null,
                sceltaCartella = SceltaCartellaFinta(),
                modelli = { Text(MODELLI_FINTI) },
                scuro = scuro,
                riduciMovimento = true,
            )
        }
        onNodeWithText(MODELLI_FINTI).assertIsDisplayed()
        catturaPng("impostazioni-modelli", w, h, scuro)
    }

    @ParameterizedTest
    @MethodSource("dimensioni")
    fun `un errore di salvataggio e mostrato`(w: Int, h: Int, scuro: Boolean) = runDesktopComposeUiTest(w, h) {
        setContent {
            SchermataImpostazioni(
                stato = STATO.copy(errore = "Impossibile salvare l'impostazione. Riprova."),
                azioni = AZIONI,
                riassunto = null,
                sceltaCartella = SceltaCartellaFinta(),
                modelli = { Text(MODELLI_FINTI) },
                scuro = scuro,
                riduciMovimento = true,
            )
        }
        onNodeWithTag("impostazioni-errore").assertIsDisplayed()
        catturaPng("impostazioni-errore", w, h, scuro)
    }

    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int, scuro: Boolean) {
        val suffisso = if (scuro) "-scuro" else ""
        val png = File(outputDir, "$nome$suffisso-${width}x$height.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }

    companion object {
        @JvmStatic
        fun dimensioni(): Stream<Arguments> = Stream.of(
            Arguments.of(1280, 800, false),
            Arguments.of(1024, 640, false),
            Arguments.of(1280, 800, true),
            Arguments.of(1024, 640, true),
        )
    }
}
