package snastro.ui.registrazione

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.testi.ETICHETTA_APRI_DOCUMENTO
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_TRASCRITTO_VUOTO
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val AZIONI_VUOTE = AzioniRegistrazione(
    riproduciDaInizio = {},
    pausa = {},
    riproduciSegmento = {},
    apriDocumento = {},
    mostraDocumentoNellaCartella = {},
    chiudiErrore = {},
    riprova = {},
)

private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

@Suppress("LongParameterList") // one parameter per SegmentoRiga field
private fun unSegmento(
    numero: Int,
    voceNumero: Int,
    inizioMs: Long,
    fineMs: Long,
    testo: String,
    inRiproduzione: Boolean = false,
) = SegmentoRiga(
    segmentoId = SegmentoId(numero),
    voceId = VoceId(voceNumero),
    etichettaVoce = "Voce $voceNumero",
    inizioMs = inizioMs,
    fineMs = fineMs,
    testo = testo,
    inRiproduzione = inRiproduzione,
)

private fun uniStato(
    segmenti: List<SegmentoRiga>,
    barra: LettoreUiStato = LettoreUiStato.Inattivo,
    audioDisponibile: Boolean = true,
    documentoPercorso: String? = "/progetti/demo.snastro/documenti/2026-03-12 Seduta.md",
    errore: String? = null,
) = RegistrazioneUiStato.Dati(
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 185_000,
    segmenti = segmenti,
    barra = barra,
    audioDisponibile = audioDisponibile,
    documentoPercorso = documentoPercorso,
    errore = errore,
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [RegistrazioneUiStato] fixture at both sizes —
 * sizing/overflow/contrast/state-rendering (AC-207/208/217/218). [SchermataRegistrazione] renders
 * directly from fixture `UiStato` values (dev-architecture `#presenter`). No Voci panel, no selection —
 * those belong to the R2 block `schermata-registrazione-identificazione` (AC-402).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioneRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-207 caricamento mostra uno scheletro del trascritto a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-207 caricamento mostra uno scheletro del trascritto a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-207 nessun parlato rilevato mostra il messaggio dedicato a 1280x800`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-207 nessun parlato rilevato mostra il messaggio dedicato a 1024x640`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `un fallimento del caricamento mostra uno stato distinto con Riprova a 1280x800`() =
        verificaErroreCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `un fallimento del caricamento mostra uno stato distinto con Riprova a 1024x640`() =
        verificaErroreCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-208 il trascritto mostra piu Voci con Segmenti sovrapposti, entrambi tenuti a 1280x800`() =
        verificaTrascrittoConSovrapposizione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-208 il trascritto mostra piu Voci con Segmenti sovrapposti, entrambi tenuti a 1024x640`() =
        verificaTrascrittoConSovrapposizione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-208 il Segmento in riproduzione e evidenziato a 1280x800`() =
        verificaSegmentoInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-208 il Segmento in riproduzione e evidenziato a 1024x640`() =
        verificaSegmentoInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-217 sorgente audio mancante disabilita la barra con un messaggio a 1280x800`() =
        verificaAudioNonDisponibile(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-217 sorgente audio mancante disabilita la barra con un messaggio a 1024x640`() =
        verificaAudioNonDisponibile(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    private fun verificaCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { SchermataRegistrazione(stato = RegistrazioneUiStato.Caricamento, azioni = AZIONI_VUOTE) }
        onNodeWithTag("registrazione-scheletro").assertIsDisplayed()
        catturaPng("registrazione-caricamento", width, height)
    }

    private fun verificaVuoto(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { SchermataRegistrazione(stato = uniStato(segmenti = emptyList()), azioni = AZIONI_VUOTE) }
        onNodeWithText(MESSAGGIO_TRASCRITTO_VUOTO).assertIsDisplayed()
        catturaPng("registrazione-vuoto", width, height)
    }

    private fun verificaErroreCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazione(
                stato = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazione-errore-caricamento").assertIsDisplayed()
        onNodeWithText(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO).assertIsDisplayed()
        onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
        catturaPng("registrazione-errore-caricamento", width, height)
    }

    private fun verificaTrascrittoConSovrapposizione(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val segmenti = listOf(
            unSegmento(1, voceNumero = 1, inizioMs = 0, fineMs = 4_000, testo = "Buongiorno a tutti."),
            // AC-208/ux-proposal Q-4: overlapping Segmenti of different Voci, both kept, no warning.
            unSegmento(2, voceNumero = 2, inizioMs = 3_000, fineMs = 6_000, testo = "Posso interrompere un attimo?"),
            unSegmento(3, voceNumero = 1, inizioMs = 6_000, fineMs = 9_500, testo = "Certo, prego."),
            unSegmento(4, voceNumero = 3, inizioMs = 9_500, fineMs = 12_000, testo = "Anche io vorrei dire una cosa."),
        )
        setContent { SchermataRegistrazione(stato = uniStato(segmenti = segmenti), azioni = AZIONI_VUOTE) }
        // Segmento 1 and Segmento 3 are both "Voce 1" (overlapping different Voci, ux-proposal Q-4) —
        // both render their own row, "Voce 1" legitimately appears twice.
        onAllNodesWithText("Voce 1").assertCountEquals(2)
        onNodeWithText("Posso interrompere un attimo?").assertIsDisplayed()
        onNodeWithText("Anche io vorrei dire una cosa.").assertIsDisplayed()
        onNodeWithText(ETICHETTA_APRI_DOCUMENTO).assertIsDisplayed()
        catturaPng("registrazione-trascritto-sovrapposto", width, height)
    }

    private fun verificaSegmentoInRiproduzione(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val segmenti = listOf(
            unSegmento(1, voceNumero = 1, inizioMs = 0, fineMs = 4_000, testo = "Buongiorno a tutti."),
            unSegmento(
                2,
                voceNumero = 2,
                inizioMs = 4_000,
                fineMs = 8_000,
                testo = "Grazie a voi.",
                inRiproduzione = true,
            ),
        )
        setContent {
            SchermataRegistrazione(
                stato = uniStato(segmenti = segmenti, barra = LettoreUiStato.Pronto(5_000, inRiproduzione = true)),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazione-segmento-2").assertIsDisplayed()
        catturaPng("registrazione-segmento-in-riproduzione", width, height)
    }

    private fun verificaAudioNonDisponibile(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazione(
                stato = uniStato(
                    segmenti = listOf(unSegmento(1, voceNumero = 1, inizioMs = 0, fineMs = 2_000, testo = "Testo.")),
                    barra = LettoreUiStato.NonDisponibile(MESSAGGIO_AUDIO_NON_DISPONIBILE),
                    audioDisponibile = false,
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithText(MESSAGGIO_AUDIO_NON_DISPONIBILE).assertIsDisplayed()
        // BarraLettore's own NonDisponibile rendering (snastro.ui.lettore, not owned by this block) simply
        // omits the clickable modifier rather than setting Compose's Disabled semantics — assert that.
        onNodeWithTag("lettore-riproduci").assert(!hasClickAction())
        onNodeWithText("Testo.").assertIsDisplayed()
        catturaPng("registrazione-audio-non-disponibile", width, height)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int) {
        val png = File(outputDir, "$nome-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
