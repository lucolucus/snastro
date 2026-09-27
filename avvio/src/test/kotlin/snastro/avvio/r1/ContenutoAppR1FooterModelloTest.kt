package snastro.avvio.r1

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.GrafoR0
import snastro.avvio.orologioApp
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.ui.ApriEsternoFinta
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelloFacoltativo
import java.nio.file.Path

private const val PIEDE_MODELLO = "shell-piede-modello-linguistico"
private const val MODELLO_ID = "llm-prova"
private const val DIMENSIONE_TOTALE = 6_200_000_000L

/**
 * Rework 1 (verifier gap on AC-S163): [NavigazioneProgettoTest]'s own AC-S163 test builds
 * [ShellProgetto] BY HAND with its own [snastro.ui.modelli.ModelliPresenter] — passing that flow
 * itself proves nothing about the production wiring line `ContenutoAppR1.kt:66`
 * (`etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede`); replacing
 * it with a disconnected flow stayed green there. This test instead runs [ContenutoAppR1] ITSELF, on a
 * BUILT graph ([AmbienteR1]'s real, opened project — as [ComposizioneR1Test]'s own `grafoR0Di`/`GrafoR1`
 * fixtures) with a [ServizioModelliFinta] this test drives — so the wiring line is the thing under
 * test (confirmed RED by temporarily breaking it, see the rework note).
 */
@OptIn(ExperimentalTestApi::class)
class ContenutoAppR1FooterModelloTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S163 sul grafo R1 costruito il piede mostra la riga solo durante InDownload`() =
        runDesktopComposeUiTest {
            AmbienteR1(radice).use { ambiente ->
                val facoltativiIniziali = mapOf(MODELLO_ID to StatoModelloFacoltativo.NonInstallato(DIMENSIONE_TOTALE))
                val servizio = ServizioModelliFinta(facoltativiIniziali = facoltativiIniziali)
                val grafo = GrafoR1(grafoR0Di(ambiente), servizio, ApriEsternoFinta())

                setContent { ContenutoAppR1(grafo, sceltaCartella = { null }) }

                attendiFinche(messaggio = "piede senza riga (NonInstallato)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.InDownload(2_100_000_000, DIMENSIONE_TOTALE),
                )
                attendiFinche(messaggio = "piede con la riga (InDownload)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertIsDisplayed()

                servizio.emettiFacoltativo(MODELLO_ID, StatoModelloFacoltativo.Installato)
                attendiFinche(messaggio = "piede di nuovo senza riga (Installato, era visibile)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.InDownload(3_000_000_000, DIMENSIONE_TOTALE),
                )
                attendiFinche(messaggio = "piede con la riga di nuovo (InDownload)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }

                servizio.emettiFacoltativo(
                    MODELLO_ID,
                    StatoModelloFacoltativo.Errore(ErroreServizioModelli.ReteAssente),
                )
                attendiFinche(messaggio = "piede senza riga (Errore, era visibile)") {
                    waitForIdle()
                    onAllNodesWithTag(PIEDE_MODELLO, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
                onNodeWithTag(PIEDE_MODELLO, useUnmergedTree = true).assertDoesNotExist()
            }
        }

    /** As [ComposizioneR1Test]'s own private `grafoR0Di`: [ambiente]'s real scope/dispatcher/session. */
    private fun grafoR0Di(ambiente: AmbienteR1) = GrafoR0(
        scope = ambiente.scope,
        io = ambiente.dispatcherUi,
        clock = orologioApp(),
        sessione = ambiente.sessione,
        elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
        cartellaProgettiPredefinita = radice.toString(),
    )
}
