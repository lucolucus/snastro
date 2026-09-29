package snastro.avvio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.kernel.RegistrazioneId
import snastro.supporto.test.attendiFinche
import snastro.ui.DestinazioneShell
import snastro.ui.testi.etichetta
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * The app's ONE content ([ContenutoApp]) on the BUILT graph ([AmbienteProgetto]): S3 carries the Riassunto tab (AC-S119
 * as wired), ONE tab selection per window survives opening another recording (AC-S121, carry-over 3), the hoisted
 * tab presenter keeps the typed Argomento across a Trascrizione ↔ Riassunto switch (carry-over 5), and 'Riassumi'
 * runs through the shared queue to a shown pronto.
 */
@OptIn(ExperimentalTestApi::class)
class ContenutoAppTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `S3 mostra la scheda Riassunto, la selezione vale per la finestra e l'Argomento digitato resta`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                val a = ambiente.registrazioneTrascritta()
                val b = ambiente.registrazioneTrascritta()
                setContent { ContenutoApp(ambiente.grafo(), sceltaCartella = { null }) }

                apri(a)
                scheda(1).performClick()
                attendi("scheda Riassunto di a") { esiste("riassunto") }
                onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("riassunto-argomento")))
                    .performTextInput("budget")
                scheda(0).performClick()
                attendi("scheda Trascrizione") { !esiste("riassunto") }
                scheda(1).performClick()
                attendi("l'Argomento digitato resta") {
                    onAllNodesWithText("budget").fetchSemanticsNodes().isNotEmpty()
                }

                onAllNodesWithText(etichetta(DestinazioneShell.REGISTRAZIONI))[0].performClick()
                apri(b)
                attendi("la scheda Riassunto resta selezionata aprendo b") { esiste("riassunto") }

                onNodeWithTag("riassunto-bottone-principale").performClick()
                attendi("il pronto di b mostrato") { esiste("riassunto-contenuto") }
            }
        }

    private fun ComposeUiTest.apri(id: RegistrazioneId) {
        attendi("riga di ${id.valore}") { esiste("registrazioni-riga-${id.valore}") }
        onNodeWithTag("registrazioni-riga-${id.valore}").performSemanticsAction(SemanticsActions.OnClick)
        attendi("S3 di ${id.valore} con le schede") { esiste("registrazione-schede") }
    }

    /** S3's own Trascrizione/Riassunto tabs (the Voci panel has a 'scheda-0' of its own). */
    private fun ComposeUiTest.scheda(indice: Int) =
        onNode(hasTestTag("scheda-$indice") and hasAnyAncestor(hasTestTag("registrazione-schede")))

    private fun ComposeUiTest.esiste(tag: String): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.attendi(messaggio: String, condizione: () -> Boolean) =
        attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
            waitForIdle()
            condizione()
        }
}
