package snastro.ui.registrazione

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.testi.messaggioRitrascrizioneParteInCorso
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.imageio.ImageIO
import kotlin.test.assertEquals

private val TRE_PARTI = (1..3).map { ParteRef(RegistrazioneId("parte-$it"), it) }

@Suppress("LongParameterList") // one parameter per fixture axis
private fun statoParte(
    numero: Int,
    totale: Int = 3,
    scheda: SchedaS3 = SchedaS3.TRASCRIZIONE,
    banner: String? = null,
    audio: Boolean = true,
    titoloIncontro: String = "Riunione di progetto",
) = RegistrazioneUiStato.Dati(
    titolo = "Parte $numero della riunione",
    dataRegistrazione = LocalDate.of(2026, 9, 30),
    durataMs = 4_500_000,
    segmenti = listOf(
        SegmentoRiga(SegmentoId(1), VoceId(1), "Voce 1", 4_000, 7_500, "Buongiorno a tutti, iniziamo."),
        SegmentoRiga(SegmentoId(2), VoceId(2), "Voce 2", 8_000, 11_500, "Ripartiamo dal punto di prima."),
    ),
    barra = if (audio) LettoreUiStato.Inattivo else LettoreUiStato.NonDisponibile("Audio non disponibile"),
    audioDisponibile = audio,
    sbobinaturaPercorso = null,
    soloLettura = banner != null,
    bannerRitrascrizione = banner,
    contenutoRiassunto = { Text("Riassunto dell'incontro · 3 parti") },
    schedaSelezionata = scheda,
    parte = if (totale > 1) {
        IntestazioneParte(numero, totale, titoloIncontro, LocalTime.of(10, 25), TRE_PARTI.take(totale))
    } else {
        null
    },
)

/**
 * `:ui:renderCheck` of S3 for one Parte of a multi-part Incontro (AC-I74/I75, INV-I3): header + switcher on
 * Parte 1 and Parte 3, the Riassunto tab with the switcher, the read-only banner from another Parte, a very long
 * Incontro title, the missing-audio banner, the loading skeleton and the 1-part screen — at 1280x800 and 1024x640,
 * light and dark. PNGs in `build/render-check/registrazione-parte-*`.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioneParteRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun scena(
        nome: String,
        stato: RegistrazioneUiStato,
        azioni: AzioniRegistrazione = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}),
        verifica: ComposeUiTest.() -> Unit,
    ) = listOf(false, true).forEach { scuro ->
        listOf(1280 to 800, 1024 to 640).forEach { (w, h) ->
            runDesktopComposeUiTest(w, h) {
                setContent { SchermataRegistrazione(stato, azioni, scuro = scuro, riduciMovimento = true) }
                verifica()
                val radici = onAllNodes(isRoot())
                val immagine = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = immagine.createGraphics()
                repeat(radici.fetchSemanticsNodes().size) { i ->
                    g.drawImage(radici[i].captureToImage().toAwtImage(), 0, 0, null)
                }
                g.dispose()
                val png = File(outputDir, "registrazione-parte-$nome-${w}x$h${if (scuro) "-scuro" else ""}.png")
                ImageIO.write(immagine, "PNG", png)
                check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
            }
        }
    }

    private fun ComposeUiTest.intestazione(numero: Int, titoloIncontro: String = "Riunione di progetto") {
        onNodeWithTag("registrazione-briciole").assertIsDisplayed()
        onNodeWithText("Registrazioni › $titoloIncontro · 3 parti").assertIsDisplayed()
        onNodeWithText("Parte $numero di 3 · 30/09/2026 10:25 · 1 h 15 min", substring = true).assertIsDisplayed()
        (0..2).forEach { onNodeWithTag("parte-$it").assertIsDisplayed() }
    }

    @Test
    fun `AC-I74 intestazione e selettore sulla Parte 1`() =
        scena("parte-1", statoParte(1)) { intestazione(1) }

    @Test
    fun `AC-I74 intestazione e selettore sulla Parte 3`() =
        scena("parte-3", statoParte(3)) { intestazione(3) }

    @Test
    fun `AC-I74 il selettore sulla scheda Riassunto resta visibile`() =
        scena("riassunto", statoParte(2, scheda = SchedaS3.RIASSUNTO)) {
            intestazione(2)
            onNodeWithText("Riassunto dell'incontro · 3 parti").assertIsDisplayed()
        }

    @Test
    fun `AC-I74 scegliere un altra parte la apre, la parte corrente non fa nulla`() {
        val aperte = mutableListOf<RegistrazioneId>()
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, vaiAllaParte = { aperte += it })
        scena("selettore", statoParte(2), azioni) {
            onNodeWithTag("parte-1").performClick()
            onNodeWithTag("parte-0").performClick()
        }
        assertEquals(setOf(TRE_PARTI[0].registrazioneId), aperte.toSet())
    }

    @Test
    fun `AC-I75 banner di sola lettura della parte 3 sulla pagina della parte 1`() =
        scena("sola-lettura", statoParte(1, banner = messaggioRitrascrizioneParteInCorso(3))) {
            intestazione(1)
            onNodeWithText("Ritrascrizione della parte 3 in corso: modifiche disabilitate fino al termine")
                .assertIsDisplayed()
        }

    @Test
    fun `AC-I74 un titolo di Incontro lungo viene troncato senza rompere l intestazione`() =
        scena(
            "titolo-lungo",
            statoParte(2, titoloIncontro = "Riunione di coordinamento trimestrale con tutti i responsabili di area"),
        ) {
            onNodeWithTag("registrazione-briciole").assertIsDisplayed()
            (0..2).forEach { onNodeWithTag("parte-$it").assertIsDisplayed() }
        }

    @Test
    fun `AC-I76 audio mancante su una parte resta come oggi`() =
        scena("audio-mancante", statoParte(2, audio = false)) {
            intestazione(2)
            onNodeWithText("Sorgente audio non disponibile").assertIsDisplayed()
        }

    @Test
    fun `AC-I76 caricamento mostra lo scheletro`() =
        scena("caricamento", RegistrazioneUiStato.Caricamento) {
            onNodeWithTag("registrazione-scheletro").assertIsDisplayed()
        }

    @Test
    fun `INV-I3 una sola parte non ha selettore ne suffisso`() =
        scena("una-parte", statoParte(1, totale = 1)) {
            onNodeWithText("Registrazioni").assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("registrazione-parti")).fetchSemanticsNodes().size)
            assertEquals(0, onAllNodes(hasTestTag("parte-0")).fetchSemanticsNodes().size)
        }
}
