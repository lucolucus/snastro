package snastro.ui.progetti

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.ui.testi.ETICHETTA_APRI_PROGETTO
import snastro.ui.testi.ETICHETTA_CAMBIA_CARTELLA
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val AZIONI_VUOTE = AzioniProgetti(
    crea = { _, _ -> },
    apri = {},
    chiudiErroreCrea = {},
    chiudiErroreApri = {},
    riprova = {},
)

/**
 * L464d: [SchermataProgetti] only ever asks its injected [SceltaCartella] — never builds its own
 * dialog — and only ever applies the RESULT, never branches on it beyond null-cancelled (own KDoc).
 */
@OptIn(ExperimentalTestApi::class)
class SchermataProgettiSceltaCartellaTest {
    @Test
    fun `Cambia cartella chiede al port con il proprio titolo e ne mostra il risultato`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val sceltaCartella = SceltaCartellaFinta(risultato = "/una/cartella/scelta")
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = "/tmp/iniziale",
                    sceltaCartella = sceltaCartella,
                )
            }

            onNodeWithText(ETICHETTA_CAMBIA_CARTELLA).performClick()

            assertEquals(listOf(ETICHETTA_CAMBIA_CARTELLA), sceltaCartella.titoliRichiesti)
            onNodeWithTag("progetti-cartella").assertTextEquals("/una/cartella/scelta")
        }

    @Test
    fun `Cambia cartella annullato lascia il valore precedente`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val sceltaCartella = SceltaCartellaFinta(risultato = null)
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = "/tmp/iniziale",
                    sceltaCartella = sceltaCartella,
                )
            }

            onNodeWithText(ETICHETTA_CAMBIA_CARTELLA).performClick()

            onNodeWithTag("progetti-cartella").assertTextEquals("/tmp/iniziale")
        }

    @Test
    fun `Apri progetto chiede al port e apre il percorso scelto`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val sceltaCartella = SceltaCartellaFinta(risultato = "/progetti/Riunione.snastro")
            var apertoCon: String? = null
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE.copy(apri = { apertoCon = it }),
                    cartellaGenitorePredefinita = "/tmp/iniziale",
                    sceltaCartella = sceltaCartella,
                )
            }

            onNodeWithTag("progetti-apri").performClick()

            assertEquals(listOf(ETICHETTA_APRI_PROGETTO), sceltaCartella.titoliRichiesti)
            assertEquals("/progetti/Riunione.snastro", apertoCon)
        }

    @Test
    fun `Apri progetto annullato non chiama apri`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val sceltaCartella = SceltaCartellaFinta(risultato = null)
            var chiamato = false
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE.copy(apri = { chiamato = true }),
                    cartellaGenitorePredefinita = "/tmp/iniziale",
                    sceltaCartella = sceltaCartella,
                )
            }

            onNodeWithTag("progetti-apri").performClick()

            assertFalse(chiamato)
        }
}
