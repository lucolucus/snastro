package snastro.ui.riassunto

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.ui.SnastroTema
import snastro.ui.testi.ETICHETTA_RIASSUNTO_COPIATO
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.test.assertEquals

/** Clipboard stand-in: keeps the last entry (the real one is the AWT system clipboard). */
private class AppuntiFinti : Clipboard {
    var ultimo: ClipEntry? = null

    override suspend fun getClipEntry(): ClipEntry? = ultimo

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        ultimo = clipEntry
    }

    override val nativeClipboard: Any get() = this
}

private const val LARGHEZZA = 1280
private const val ALTEZZA = 800

private val AZIONI = AzioniRiassunto({}, {}, {}, {}, {}, {}, {})

private val CONTENUTO = ContenutoUi(
    superato = false,
    sommario = "Un sommario.",
    decisioni = listOf(ElementoUi("Si parte lunedì", emptyList())),
    azioni = emptyList(),
    questioniAperte = emptyList(),
    puntiChiave = emptyList(),
    omessiTesto = null,
    metadatiTesto = "Lunghezza massima: 2000 parole",
)

@OptIn(ExperimentalTestApi::class)
class SchedaRiassuntoCopiaTest {
    @Test
    fun `Copia mette il riassunto negli appunti e conferma`() = runDesktopComposeUiTest(LARGHEZZA, ALTEZZA) {
        val appunti = AppuntiFinti()
        setContent { Scheda(CONTENUTO, appunti) }

        onNodeWithTag("riassunto-copia").performClick()
        waitForIdle()

        assertEquals(testoRiassuntoDaCopiare(CONTENUTO), appunti.testo())
        onNodeWithTag("riassunto-copia").assertTextContains(ETICHETTA_RIASSUNTO_COPIATO)
    }

    @Test
    fun `EX-20 EX-26 solo sommario, niente sezioni e Copia copia il sommario`() =
        runDesktopComposeUiTest(LARGHEZZA, ALTEZZA) {
            val appunti = AppuntiFinti()
            val soloSommario = CONTENUTO.copy(decisioni = emptyList())
            setContent { Scheda(soloSommario, appunti) }

            listOf("Decisioni", "Questioni aperte", "Azioni", "Punti chiave").forEach {
                onAllNodesWithText(it).assertCountEquals(0)
            }
            onNodeWithTag("riassunto-copia").performClick()
            waitForIdle()

            assertEquals("Un sommario.", appunti.testo())
        }

    @Composable
    private fun Scheda(contenuto: ContenutoUi, appunti: AppuntiFinti) {
        SnastroTema(scuro = false, riduciMovimento = true) {
            CompositionLocalProvider(LocalClipboard provides appunti) {
                SchedaRiassunto(
                    RiassuntoUiStato.Dati(
                        modello = ModelloUi.Installato,
                        richiesta = null,
                        fallimentoTesto = null,
                        nonDisponibileTesto = null,
                        contenuto = contenuto,
                        argomento = ArgomentoUiStato("", "0/200", null),
                        lunghezzaMassima = LunghezzaMassimaUiStato.Testo(2_000),
                    ),
                    AZIONI,
                )
            }
        }
    }

    private fun AppuntiFinti.testo(): Any =
        (ultimo?.nativeClipEntry as Transferable).getTransferData(DataFlavor.stringFlavor)
}
