package snastro.avvio.r2

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.attendiFinche
import snastro.ui.ApriEsternoFinta
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelloFacoltativo
import java.nio.file.Path

private const val PIEDE_MODELLO = "shell-piede-modello-linguistico"
private const val MODELLO_ID = "llm-prova"
private const val DIMENSIONE_TOTALE = 6_200_000_000L

/**
 * Rework 1 (verifier gap on AC-S163) — the R2 half of `ContenutoAppR1FooterModelloTest`'s KDoc: runs
 * the real [ContenutoAppR2] (production wiring line `ContenutoAppR2.kt:82`) over [AmbienteR2]'s BUILT
 * graph ([AmbienteR2.grafoR0], the same one [AmbienteR2.grafo] itself builds at AmbienteR2.kt:160),
 * with a [ServizioModelliFinta] this test drives instead of [AmbienteR2]'s fixed `Pronti` one.
 */
@OptIn(ExperimentalTestApi::class)
class ContenutoAppR2FooterModelloTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S163 sul grafo R2 costruito il piede mostra la riga solo durante InDownload`() =
        runDesktopComposeUiTest {
            AmbienteR2(radice).use { ambiente ->
                val facoltativiIniziali = mapOf(MODELLO_ID to StatoModelloFacoltativo.NonInstallato(DIMENSIONE_TOTALE))
                val servizio = ServizioModelliFinta(facoltativiIniziali = facoltativiIniziali)
                val grafo = GrafoR2(ambiente.grafoR0, servizio, ApriEsternoFinta())

                setContent { ContenutoAppR2(grafo, sceltaCartella = { null }) }

                attendiFinche(messaggio = "piede senza riga (NonInstallato)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.InDownload(1_000_000_000, DIMENSIONE_TOTALE),
                )
                attendiFinche(messaggio = "piede con la riga (InDownload)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertIsDisplayed()

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.Errore(ErroreServizioModelli.ReteAssente),
                )
                attendiFinche(messaggio = "piede senza riga (Errore, era visibile)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.InDownload(4_000_000_000, DIMENSIONE_TOTALE),
                )
                attendiFinche(messaggio = "piede con la riga di nuovo (InDownload)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }

                servizio.emettiFacoltativo(MODELLO_ID, StatoModelloFacoltativo.Installato)
                attendiFinche(messaggio = "piede senza riga (Installato, era visibile)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertDoesNotExist()
            }
        }
}
