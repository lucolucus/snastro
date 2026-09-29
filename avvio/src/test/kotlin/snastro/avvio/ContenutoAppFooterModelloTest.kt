package snastro.avvio

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.supporto.test.attendiFinche
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelloFacoltativo
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

private const val PIEDE_MODELLO = "shell-piede-modello-linguistico"
private const val MODELLO_ID = "llm-prova"
private const val DIMENSIONE_TOTALE = 6_200_000_000L

/**
 * Rework 1 (verifier gap on AC-S163): `NavigazioneProgettoTest`'s own AC-S163 test builds `ShellProgetto` BY HAND with
 * its own `ModelliPresenter` — passing that flow itself proves nothing about the production wiring line of
 * [ContenutoApp] (`etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede`). These tests
 * run [ContenutoApp] ITSELF, on a BUILT graph ([AmbienteProgetto]'s real, opened project) with a [ServizioModelliFinta]
 * the test drives — so the wiring line is the thing under test. ADR 0030: the two former per-release variants are now
 * two sequences over the ONE content.
 */
@OptIn(ExperimentalTestApi::class)
class ContenutoAppFooterModelloTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S163 il piede mostra la riga solo durante InDownload, poi Installato la toglie`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                val servizio = servizio()
                setContent { ContenutoApp(ambiente.grafo(servizio), sceltaCartella = { null }) }
                attendiPiede(visibile = false, "piede senza riga (NonInstallato)")

                servizio.emetti(StatoModelloFacoltativo.InDownload(2_100_000_000, DIMENSIONE_TOTALE))
                attendiPiede(visibile = true, "piede con la riga (InDownload)")
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertIsDisplayed()

                servizio.emetti(StatoModelloFacoltativo.Installato)
                attendiPiede(visibile = false, "piede di nuovo senza riga (Installato, era visibile)")

                servizio.emetti(StatoModelloFacoltativo.InDownload(3_000_000_000, DIMENSIONE_TOTALE))
                attendiPiede(visibile = true, "piede con la riga di nuovo (InDownload)")

                servizio.emetti(StatoModelloFacoltativo.Errore(ErroreServizioModelli.ReteAssente))
                attendiPiede(visibile = false, "piede senza riga (Errore, era visibile)")
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertDoesNotExist()
            }
        }

    @Test
    fun `AC-S163 il piede mostra la riga solo durante InDownload, poi Errore la toglie`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                val servizio = servizio()
                setContent { ContenutoApp(ambiente.grafo(servizio), sceltaCartella = { null }) }
                attendiPiede(visibile = false, "piede senza riga (NonInstallato)")

                servizio.emetti(StatoModelloFacoltativo.InDownload(1_000_000_000, DIMENSIONE_TOTALE))
                attendiPiede(visibile = true, "piede con la riga (InDownload)")
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertIsDisplayed()

                servizio.emetti(StatoModelloFacoltativo.Errore(ErroreServizioModelli.ReteAssente))
                attendiPiede(visibile = false, "piede senza riga (Errore, era visibile)")

                servizio.emetti(StatoModelloFacoltativo.InDownload(4_000_000_000, DIMENSIONE_TOTALE))
                attendiPiede(visibile = true, "piede con la riga di nuovo (InDownload)")

                servizio.emetti(StatoModelloFacoltativo.Installato)
                attendiPiede(visibile = false, "piede senza riga (Installato, era visibile)")
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertDoesNotExist()
            }
        }

    private fun servizio() = ServizioModelliFinta(
        facoltativiIniziali = mapOf(MODELLO_ID to StatoModelloFacoltativo.NonInstallato(DIMENSIONE_TOTALE)),
    )

    private fun ServizioModelliFinta.emetti(stato: StatoModelloFacoltativo) = emettiFacoltativo(MODELLO_ID, stato)

    private fun ComposeUiTest.attendiPiede(visibile: Boolean, messaggio: String) =
        attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
            waitForIdle()
            onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() == visibile
        }
}
