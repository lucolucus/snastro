package snastro.ui.registrazione

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
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
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.testi.descrizioneAscoltaVoce
import snastro.ui.testi.testoTraParti
import snastro.ui.testi.testoUnione
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.assertEquals

private val ESTRATTO_A = EstrattoRef(RegistrazioneId("id-1"), listOf(IntervalloMs(0, 500)))
private val ESTRATTO_B = EstrattoRef(RegistrazioneId("id-2"), listOf(IntervalloMs(1_000, 1_500)))
private val COPPIA = CoppiaTraParti(VoceId(1), 1, ESTRATTO_A, VoceId(5), 2, ESTRATTO_B)

private fun cartaDaIdentificare(n: Int) = CartaVoce(
    VoceId(n),
    "Voce $n",
    ContenutoCarta.DaIdentificare(StatoProposta.Pronta(emptyList(), nuovoEvidenziato = true), galleriaVuota = true),
    altreVoci = listOf(OpzioneVoce(VoceId(if (n == 1) 5 else 1), "Voce ${if (n == 1) 5 else 1}")),
)

private fun pannelloTraParti(
    coppia: CoppiaTraParti? = COPPIA,
    estratti: Boolean = true,
    unioni: List<PropostaDiUnione> = emptyList(),
    abilitata: Boolean = true,
) = PannelloVoci(
    carte = listOf(cartaDaIdentificare(1), cartaDaIdentificare(5)),
    parlantiAttivi = emptyList(),
    unioni = unioni,
    estrattiDisponibili = estratti,
    unioneAbilitata = abilitata,
    traParti = coppia,
)

private fun statoTraParti(pannello: PannelloVoci) = RegistrazioneUiStato.Dati(
    titolo = "Riunione · 2 parti",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 185_000,
    segmenti = listOf(
        SegmentoRiga(SegmentoId(1), VoceId(1), "Voce 1", 4_000, 7_500, "Buongiorno a tutti, iniziamo."),
        SegmentoRiga(SegmentoId(2), VoceId(5), "Voce 5", 8_000, 11_500, "Ripartiamo dal punto di prima."),
    ),
    barra = LettoreUiStato.Inattivo,
    audioDisponibile = pannello.estrattiDisponibili,
    sbobinaturaPercorso = null,
    pannello = pannello,
    contenutoRiassunto = {},
)

/**
 * `:ui:renderCheck` of the cross-Parte banner (AC-I83/AC-I84): default, long list of Voci under it, extracts
 * unavailable, and the Proposta di unione taking the single banner slot — at 1280x800 and 1024x640, light and
 * dark. PNGs in `build/render-check/registrazione-tra-parti-*`.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioneTraPartiRenderCheckTest {
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
                val png = File(outputDir, "registrazione-tra-parti-$nome-${w}x$h${if (scuro) "-scuro" else ""}.png")
                ImageIO.write(immagine, "PNG", png)
                check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
            }
        }
    }

    @Test
    fun `AC-I83 il banner mostra il testo, i due estratti e Unisci senza No`() {
        val suonati = mutableListOf<EstrattoRef>()
        var unite: Pair<VoceId, VoceId>? = null
        val azioni = AzioniRegistrazione(
            {}, {}, {}, {}, {}, {}, {},
            unisci = { a, b -> unite = a to b },
            riproduciEstratto = { suonati += it },
        )
        scena("banner", statoTraParti(pannelloTraParti()), azioni) {
            onNodeWithTag("voci-tra-parti-1-5").assertIsDisplayed()
            onNodeWithText(testoTraParti(1, 1, 5)).assertIsDisplayed()
            onNodeWithTag("voci-tra-parti-estratto-1").assertIsDisplayed().assertIsEnabled().performClick()
            onNodeWithTag("voci-tra-parti-estratto-5").assertIsDisplayed().assertIsEnabled().performClick()
            onNodeWithContentDescription(descrizioneAscoltaVoce(1)).assertIsDisplayed() // L191
            onNodeWithContentDescription(descrizioneAscoltaVoce(5)).assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("voci-tra-parti-no")).fetchSemanticsNodes().size)
            assertEquals(0, onAllNodes(hasText("No")).fetchSemanticsNodes().size)
            onNodeWithText("Unisci").assertIsDisplayed().performClick()
        }
        assertEquals(listOf(ESTRATTO_A, ESTRATTO_B, ESTRATTO_A, ESTRATTO_B), suonati.take(4))
        assertEquals(VoceId(1) to VoceId(5), unite)
    }

    @Test
    fun `AC-I83 D-0057 la parte del testo e quella da cui suona l estratto anche se e successiva all altra`() {
        // voce A plays from Parte 2, voce B from Parte 1: the bracket still names voce A's Parte.
        val tardi = CoppiaTraParti(VoceId(1), 2, ESTRATTO_B, VoceId(5), 1, ESTRATTO_A)
        assertEquals("Voce 5 e Voce 1 (parte 2) sembrano la stessa persona", testoTraParti(1, tardi.parteA, 5))
        scena("banner-parte-a-dopo-b", statoTraParti(pannelloTraParti(coppia = tardi))) {
            onNodeWithText("Voce 5 e Voce 1 (parte 2) sembrano la stessa persona").assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I83 estratti non disponibili disabilita i due play`() =
        scena("senza-estratti", statoTraParti(pannelloTraParti(estratti = false))) {
            onNodeWithTag("voci-tra-parti-estratto-1").assertIsNotEnabled()
            onNodeWithTag("voci-tra-parti-estratto-5").assertIsNotEnabled()
        }

    @Test
    fun `AC-I84 con una Proposta di unione c e solo il suo banner`() =
        scena(
            "solo-unione",
            statoTraParti(
                pannelloTraParti(
                    coppia = null,
                    unioni = listOf(PropostaDiUnione(VoceId(1), VoceId(5), MARCO.parlanteId, "Marco")),
                ),
            ),
        ) {
            onNodeWithText(testoUnione(1, 5, "Marco")).assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("voci-tra-parti-1-5")).fetchSemanticsNodes().size)
        }

    @Test
    fun `AC-I84 senza coppia nessun banner tra parti`() =
        scena("nessuna", statoTraParti(pannelloTraParti(coppia = null))) {
            assertEquals(0, onAllNodes(hasTestTag("voci-tra-parti-1-5")).fetchSemanticsNodes().size)
        }
}
