package snastro.avvio

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.supporto.test.attendiFinche
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Impostazioni from S1's header (full-window, back to S1) and from an open project's sidebar footer. */
@OptIn(ExperimentalTestApi::class)
class ContenutoAppImpostazioniTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `da S1 l ingranaggio apre Impostazioni su Generali, anche a modelli mancanti, e Indietro torna`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                ambiente.sessione.chiudi()
                val mancanti = ServizioModelliFinta(StatoModelli.Mancanti(numero = 2, totaleByte = 1))
                setContent { ContenutoApp(ambiente.grafo(mancanti), sceltaCartella = { null }) }
                attendi("S1") { esiste("progetti-impostazioni") }

                onNodeWithTag("progetti-impostazioni").performClick()
                attendi("Impostazioni › Generali") { esiste("impostazioni-tema") }

                onNodeWithTag("impostazioni-indietro").performClick()
                attendi("di nuovo S1") { esiste("progetti-impostazioni") }
            }
        }

    @Test
    fun `con un progetto aperto il piede apre Impostazioni e Riassunto mostra la lunghezza del progetto`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                setContent { ContenutoApp(ambiente.grafo(), sceltaCartella = { null }) }
                attendi("la shell del progetto") { esiste("shell-piede") }

                onNodeWithTag("shell-piede").performClick()
                attendi("Impostazioni") { esiste("impostazioni") }
                onNodeWithTag("impostazioni-indietro").assertDoesNotExist()

                onNodeWithTag("impostazioni-sezione-riassunto").performClick()
                attendi("l'editor della lunghezza") { esiste("impostazioni-campo-parole") }
            }
        }

    private fun ComposeUiTest.esiste(tag: String): Boolean =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.attendi(messaggio: String, condizione: () -> Boolean) =
        attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
            waitForIdle()
            condizione()
        }
}
