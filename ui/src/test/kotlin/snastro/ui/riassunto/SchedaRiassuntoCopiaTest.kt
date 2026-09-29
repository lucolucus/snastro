package snastro.ui.riassunto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
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
    fun `Copia mette il riassunto negli appunti e conferma`() = runDesktopComposeUiTest(1280, 800) {
        val appunti = AppuntiFinti()
        setContent {
            SnastroTema(scuro = false, riduciMovimento = true) {
                CompositionLocalProvider(LocalClipboard provides appunti) {
                    SchedaRiassunto(
                        RiassuntoUiStato.Dati(
                            modello = ModelloUi.Installato,
                            richiesta = null,
                            fallimentoTesto = null,
                            nonDisponibileTesto = null,
                            contenuto = CONTENUTO,
                            argomento = ArgomentoUiStato("", "0/200", null),
                            lunghezzaMassima = LunghezzaMassimaUiStato.Testo(2_000),
                        ),
                        AZIONI,
                    )
                }
            }
        }

        onNodeWithTag("riassunto-copia").performClick()
        waitForIdle()

        val copiato = (appunti.ultimo?.nativeClipEntry as Transferable).getTransferData(DataFlavor.stringFlavor)
        assertEquals(testoRiassuntoDaCopiare(CONTENUTO), copiato)
        onNodeWithTag("riassunto-copia").assertTextContains(ETICHETTA_RIASSUNTO_COPIATO)
    }
}
