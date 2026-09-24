package snastro.ui.registrazioni

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.ui.stile.ColoriChiari
import snastro.ui.testi.ETICHETTA_DA_IDENTIFICARE
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import java.time.LocalDate
import kotlin.test.assertEquals

private val REG_A = RegistrazioneId("id-a")
private val REG_B = RegistrazioneId("id-b")
private val REG_X = RegistrazioneId("id-x")
private val DATA: LocalDate = LocalDate.of(2026, 3, 12)
private const val DUE_MINUTI_CINQUE_MS = 125_000L

private val AZIONI_VUOTE = AzioniRegistrazioni(
    importa = {},
    modificaData = { _, _ -> },
    rinomina = { _, _ -> },
    riproduci = {},
    pausa = {},
    avviaElaborazione = {},
    modificaNumeroPersone = { _, _ -> },
    apriRiga = {},
    chiudiErrore = {},
    chiudiErroreRiga = {},
    riprova = {},
    ritrascrivi = {},
    annullaRitrascrivi = {},
    confermaRitrascrivi = {},
    annullaElaborazione = {},
)

private fun riga(id: RegistrazioneId, titolo: String) =
    RigaRegistrazione(registrazioneId = id, titolo = titolo, dataRegistrazione = DATA, durataMs = DUE_MINUTI_CINQUE_MS)

/** S2 view behaviour no presenter test can prove: the header (AC-574), the row's right side (AC-575),
 * the empty DropZone while importing (rework cycle 2 LOW #7) and row identity (rework cycle 2 MED #2). */
@OptIn(ExperimentalTestApi::class)
class RegistrazioniRigheTest {
    @Test
    fun `AC-574 l intestazione dice n registrazioni e la durata estesa totale, con Importa Primario`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = listOf(riga(REG_A, "Uno"), riga(REG_B, "Due"))),
                    azioni = AZIONI_VUOTE,
                    scuro = false,
                    riduciMovimento = true,
                )
            }
            onNodeWithText("2 registrazioni · 4 min").assertIsDisplayed()
            onNodeWithText(ETICHETTA_IMPORTA_FILE).assertIsDisplayed()
            // Primario = the `accent` fill (Secondario is `raised`): sample inside the left padding.
            val immagine = onNodeWithTag("registrazioni-importa").captureToImage().toPixelMap()
            assertEquals(ColoriChiari.accent.toArgb(), immagine[PIXEL_DENTRO, immagine.height / 2].toArgb())
        }

    @Test
    fun `AC-575 una riga Trascritta tiene il campo precompilato e Ritrascrivi, il mix mostra Da identificare`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            riga(REG_A, "Trascritta").copy(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                ritrascriviDisponibile = true,
                                numeroPersone = "3",
                                identificazione = IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 0),
                            ),
                            riga(REG_B, "Riunione").copy(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                identificazione = IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 1),
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    riduciMovimento = true,
                )
            }
            onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("registrazioni-numero-persone-${REG_A.valore}")),
                useUnmergedTree = true,
            ).assertTextEquals("3")
            onNodeWithText(ETICHETTA_RITRASCRIVI).assertIsDisplayed()
            onNodeWithText(ETICHETTA_DA_IDENTIFICARE).assertIsDisplayed()
        }

    @Test
    fun `AC-576 Scegli file e disabilitato mentre un importo e in corso`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = emptyList(), importoInCorso = true),
                    azioni = AZIONI_VUOTE,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-scegli-file").assertIsNotEnabled()
            onNodeWithTag("registrazioni-importa").assertIsNotEnabled()
        }

    @Test
    fun `una modifica di data in corso resta sulla sua riga quando una riga viene inserita sopra`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val modifiche = mutableListOf<Pair<RegistrazioneId, LocalDate>>()
            val stato = mutableStateOf<RegistrazioniUiStato>(
                RegistrazioniUiStato.Dati(righe = listOf(riga(REG_A, "A"), riga(REG_B, "B"))),
            )
            setContent {
                SchermataRegistrazioni(
                    stato = stato.value,
                    azioni = AZIONI_VUOTE.copy(modificaData = { id, data -> modifiche += id to data }),
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-data-${REG_B.valore}", useUnmergedTree = true).performClick()
            onNodeWithTag("registrazioni-data-${REG_B.valore}", useUnmergedTree = true)
                .performTextReplacement("01/02/2026")

            stato.value = RegistrazioniUiStato.Dati(
                righe = listOf(riga(REG_X, "X"), riga(REG_A, "A"), riga(REG_B, "B")),
            )
            waitForIdle()

            onNodeWithTag("registrazioni-data-${REG_B.valore}", useUnmergedTree = true).performImeAction()
            assertEquals(listOf(REG_B to LocalDate.of(2026, 2, 1)), modifiche)
        }

    private companion object {
        const val PIXEL_DENTRO = 4
    }
}
