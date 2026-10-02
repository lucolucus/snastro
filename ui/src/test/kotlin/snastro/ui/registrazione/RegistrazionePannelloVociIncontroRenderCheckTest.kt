package snastro.ui.registrazione

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.Candidato
import snastro.parlanti.applicazione.porte.Fascia
import snastro.ui.lettore.LettoreUiStato
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.assertEquals

private val PARTE_1 = RegistrazioneId("parte-1")
private val V5 = VoceId(5)

private val CANDIDATO_PARTE_1 = Candidato(
    MARCO.parlanteId,
    "Marco",
    TipoParlanteVista.RICORRENTE,
    Fascia.FORTE,
    EstrattoRef(PARTE_1, listOf(IntervalloMs(0, 1_000))),
)

private fun cartaParte2() = CartaVoce(
    V2,
    "Voce 2",
    ContenutoCarta.DaIdentificare(StatoProposta.Pronta(listOf(CANDIDATO_PARTE_1), nuovoEvidenziato = false), false),
    altreVoci = listOf(OpzioneVoce(VoceId(1), "Voce 1")),
    altreParti = listOf(1, 3),
    vociAltreParti = listOf(OpzioneVoce(V5, "Marco", listOf(1), "Marco")),
)

private fun cartaQui() = CartaVoce(
    VoceId(1),
    "Voce 1",
    ContenutoCarta.DaIdentificare(StatoProposta.Pronta(emptyList(), nuovoEvidenziato = true), galleriaVuota = true),
    altreVoci = listOf(OpzioneVoce(V2, "Voce 2")),
    vociAltreParti = listOf(OpzioneVoce(V5, "Marco", listOf(1), "Marco")),
)

private fun pannelloParte2(somiglianza: PannelloSomiglianza? = null) = PannelloVoci(
    carte = listOf(cartaParte2(), cartaQui()),
    parlantiAttivi = listOf(MARCO),
    unioni = emptyList(),
    estrattiDisponibili = true,
    unioneAbilitata = true,
    somiglianza = somiglianza,
    altreParti = mapOf(PARTE_1 to 1, RegistrazioneId("parte-3") to 3),
)

private fun statoParte2(pannello: PannelloVoci) = RegistrazioneUiStato.Dati(
    titolo = "Riunione · 3 parti",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 185_000,
    segmenti = listOf(
        SegmentoRiga(SegmentoId(1), VoceId(1), "Voce 1", 4_000, 7_500, "Buongiorno a tutti, iniziamo."),
        SegmentoRiga(SegmentoId(2), V2, "Voce 2", 8_000, 11_500, "Ripartiamo dal punto di prima."),
    ),
    barra = LettoreUiStato.Inattivo,
    audioDisponibile = true,
    sbobinaturaPercorso = null,
    pannello = pannello,
    contenutoRiassunto = {},
)

private fun anteprimaPerParte() = PannelloSomiglianza(
    abilitato = false,
    suggerimento = null,
    riferimenti = "Riferimenti: Marco, Giulia",
    avvisoTuttaLaVoce = null,
    nonToccate = null,
    fase = FaseSomiglianza.Anteprima(
        "Sposterò 11 frasi, 1 incerta resta dove è",
        listOf("Voce 3 → Anna: 8 (parte 1: 3, parte 2: 5)", "Voce 4 → Marco: 3 (parte 2: 3)"),
        applicabile = true,
        inApplicazione = false,
    ),
)

/**
 * `:ui:renderCheck` of the Voci panel of a Parte of a multi-Parte Incontro (AC-I77..AC-I79): 'anche in parte 1, 3',
 * the 'Unisci con' groups, 'estratto · parte 1' and the per-Parte preview line, at 1280x800 and 1024x640, light and
 * dark. PNGs in `build/render-check/pannello-voci-incontro-*`.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazionePannelloVociIncontroRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun scena(
        nome: String,
        stato: RegistrazioneUiStato.Dati,
        azioni: AzioniRegistrazione = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}),
        verifica: ComposeUiTest.() -> Unit,
    ) = listOf(false, true).forEach { scuro ->
        listOf(1280 to 800, 1024 to 640).forEach { (w, h) ->
            runDesktopComposeUiTest(w, h) {
                setContent { SchermataRegistrazione(stato, azioni, scuro = scuro, riduciMovimento = true) }
                onNodeWithTag("voci-pannello").assertIsDisplayed()
                verifica()
                val radici = onAllNodes(isRoot())
                val immagine = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = immagine.createGraphics()
                repeat(radici.fetchSemanticsNodes().size) { i ->
                    g.drawImage(radici[i].captureToImage().toAwtImage(), 0, 0, null)
                }
                g.dispose()
                val png = File(outputDir, "pannello-voci-incontro-$nome-${w}x$h${if (scuro) "-scuro" else ""}.png")
                ImageIO.write(immagine, "PNG", png)
                check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
            }
        }
    }

    @Test
    fun `AC-I77 AC-I78 la carta dice anche in parte e l estratto di un altra parte lo dichiara`() =
        scena("carte", statoParte2(pannelloParte2())) {
            onNodeWithTag("voce-2-altre-parti").assertIsDisplayed()
            onNodeWithText("anche in parte 1, 3").assertIsDisplayed()
            // Below the fold at 1024x640 (the page scrolls): present in the tree, shown in the 1280x800 PNG.
            onNodeWithTag("voce-2-candidato-0-parte").assertExists()
            onNodeWithText("estratto · parte 1").assertExists()
        }

    @Test
    fun `AC-I77 Unisci con ha i due gruppi, con parte n sulle Voci di un altra parte`() =
        scena("unisci-con", statoParte2(pannelloParte2())) {
            onNodeWithTag("voce-2-cambia").performClick()
            onNodeWithText("In questa parte").assertIsDisplayed()
            onNodeWithText("In altre parti").assertIsDisplayed()
            onNodeWithText("parte 1").assertIsDisplayed()
            onNodeWithTag("voce-2-unisci-5").assertIsDisplayed()
        }

    @Test
    fun `AC-I77 scegliere una Voce di un altra parte unisce la Voce di questa carta con quella`() {
        val unite = mutableListOf<Pair<VoceId, VoceId>>()
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, unisci = { a, b -> unite += a to b })
        runDesktopComposeUiTest(1280, 800) {
            setContent { SchermataRegistrazione(statoParte2(pannelloParte2()), azioni, scuro = false) }
            onNodeWithTag("voce-2-cambia").performClick()
            onNodeWithTag("voce-2-unisci-5").performClick()
        }
        assertEquals(listOf(VoceId(2) to V5), unite)
    }

    @Test
    fun `AC-I79 l anteprima di Riassegna per somiglianza mostra le parti`() =
        scena("anteprima", statoParte2(pannelloParte2(anteprimaPerParte()))) {
            onNodeWithText("Voce 3 → Anna: 8 (parte 1: 3, parte 2: 5)").assertIsDisplayed()
            onNodeWithText("Voce 4 → Marco: 3 (parte 2: 3)").assertIsDisplayed()
        }
}
