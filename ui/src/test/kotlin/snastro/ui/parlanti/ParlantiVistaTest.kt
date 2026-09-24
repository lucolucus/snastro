package snastro.ui.parlanti

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.ui.stile.ColoriChiari
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private val AZIONI_VUOTE = AzioniParlanti(
    rinomina = { _, _ -> },
    promuovi = {},
    riproduci = {},
    chiediConfermaEliminazione = {},
    annullaEliminazione = {},
    confermaEliminazione = {},
    chiudiErroreRiga = {},
    chiudiErrore = {},
    riprova = {},
)

private val PARLANTE_1 = ParlanteId("id-1")

private fun unaRiga(tipoParlante: TipoParlanteVista = TipoParlanteVista.RICORRENTE) = RigaParlante(
    parlanteId = PARLANTE_1,
    nome = "Marco",
    tipoParlante = tipoParlante,
    numImpronte = 3,
    numRegistrazioni = 2,
    ultimaApparizione = LocalDate.of(2026, 3, 12),
    riproduzioneAbilitata = true,
)

/**
 * S4 view behaviour no presenter test can prove (RC-2): the empty state's layout (L735b), the More
 * menu's focus return (L742b) and the Nome field's focused underline (L742c).
 */
@OptIn(ExperimentalTestApi::class)
class ParlantiVistaTest {
    @Test
    fun `L735b lo stato vuoto mostra un icona e il messaggio centrato invece di una riga sola a sinistra`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(),
                    azioni = AZIONI_VUOTE,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("parlanti-vuoto").assertIsDisplayed()
            onNodeWithTag("parlanti-vuoto-icona", useUnmergedTree = true).assertIsDisplayed()
        }

    @Test
    fun `L742b il focus torna al pulsante More dopo aver scelto Elimina dal menu`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(ricorrenti = listOf(unaRiga())),
                    azioni = AZIONI_VUOTE,
                    riduciMovimento = true,
                )
            }
            val pulsanteMore = onNodeWithTag("parlanti-altre-azioni-${PARLANTE_1.valore}")
            pulsanteMore.performClick()

            onNodeWithTag("parlanti-elimina-${PARLANTE_1.valore}").performClick()

            pulsanteMore.assertIsFocused()
        }

    @Test
    fun `L742b il focus torna al pulsante More anche chiudendo il menu senza scegliere nulla`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(occasionali = listOf(unaRiga(TipoParlanteVista.OCCASIONALE))),
                    azioni = AZIONI_VUOTE,
                    riduciMovimento = true,
                )
            }
            val pulsanteMore = onNodeWithTag("parlanti-altre-azioni-${PARLANTE_1.valore}")
            pulsanteMore.performClick()
            onNodeWithTag("parlanti-promuovi-${PARLANTE_1.valore}").assertIsDisplayed() // the menu is open

            // dismiss without choosing an item, e.g. an outside click/Esc — modelled here by re-clicking
            // the button itself, which Compose's DropdownMenu treats as a dismiss request too.
            pulsanteMore.performClick()

            pulsanteMore.assertIsFocused()
        }

    @Test
    fun `L742c il campo Nome mostra una sottolineatura lineStrong solo quando ha il focus`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(ricorrenti = listOf(unaRiga())),
                    azioni = AZIONI_VUOTE,
                    scuro = false,
                    riduciMovimento = true,
                )
            }
            val campo = onNodeWithTag("parlanti-nome-${PARLANTE_1.valore}", useUnmergedTree = true)
            val coloreAtteso = ColoriChiari.lineStrong.toArgb()

            val primaDelFocus = campo.captureToImage().toPixelMap()
            assertNotEquals(coloreAtteso, primaDelFocus[primaDelFocus.width / 2, primaDelFocus.height - 1].toArgb())

            campo.performClick()
            campo.assertIsFocused()

            val dopoIlFocus = campo.captureToImage().toPixelMap()
            assertEquals(coloreAtteso, dopoIlFocus[dopoIlFocus.width / 2, dopoIlFocus.height - 1].toArgb())
        }
}
