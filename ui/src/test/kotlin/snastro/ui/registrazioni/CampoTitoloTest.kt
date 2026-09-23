package snastro.ui.registrazioni

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import kotlin.test.assertEquals

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun rigaDiProva(titolo: String) =
    RigaRegistrazione(registrazioneId = REG_1, titolo = titolo, dataRegistrazione = DATA_1, durataMs = 60_000)

private val AZIONI_VUOTE = AzioniRegistrazioni(
    importa = {},
    modificaData = { _, _ -> },
    rinomina = { _, _ -> },
    riproduci = {},
    pausa = {},
    avviaElaborazione = {},
    apriRiga = {},
    chiudiErrore = {},
    chiudiErroreRiga = {},
    riprova = {},
)

/**
 * fix-batch-12 #3: `CampoTitolo`'s local buffer behaviour that no presenter test alone can prove —
 * the VIEW's own resync after a refused rename, and Esc reverting an in-progress edit. A tiny fake
 * "presenter" (a `remember`ed [RegistrazioniUiStato] mutated by `rinomina`, async and M3-guarded like
 * the real [RegistrazioniPresenter.suRiga]) stands in so the test exercises the real
 * [SchermataRegistrazioni] composable end to end — an IME "Done" here also blurs the field (same as a
 * real on-screen keyboard), so a second `sottometti()` fires from `onFocusChanged`; M3 (`riga.
 * operazioneInCorso` already true) is what makes that second call a no-op in production, exactly as
 * [RegistrazioniRinominaTest]'s own M3 test proves at the presenter level.
 */
@OptIn(ExperimentalTestApi::class)
class CampoTitoloTest {
    @Test
    fun `un rinomina rifiutato ripristina il titolo salvato mantenendo l errore inline`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val erroreSimulato = "Titolo gia usato in questo progetto."
            setContent {
                var stato by remember {
                    val righeIniziali = listOf(rigaDiProva("Seduta"))
                    mutableStateOf<RegistrazioniUiStato>(RegistrazioniUiStato.Dati(righe = righeIniziali))
                }
                val scope = rememberCoroutineScope()
                SchermataRegistrazioni(
                    stato = stato,
                    azioni = AZIONI_VUOTE.copy(
                        rinomina = { id, nuovo ->
                            val righeAttuali = (stato as RegistrazioniUiStato.Dati).righe
                            val rigaAttuale = righeAttuali.single { it.registrazioneId == id }
                            if (!rigaAttuale.operazioneInCorso) { // M3
                                stato = mutaRiga(stato, id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
                                scope.launch {
                                    delay(1) // async, come il vero RegistrazioniPresenter.suRiga
                                    stato = mutaRiga(stato, id) {
                                        it.copy(operazioneInCorso = false, erroreRiga = erroreSimulato)
                                    }
                                }
                            }
                        },
                    ),
                )
            }

            val campo = onNodeWithTag("registrazioni-titolo-${REG_1.valore}", useUnmergedTree = true)
            campo.performTextReplacement("Intervista")
            campo.performImeAction()
            // Waiting on the FIELD'S OWN text (not just that the row turned into the refused state) —
            // several intermediate state changes can coalesce into fewer recompositions than that.
            waitUntil(timeoutMillis = 5_000) {
                val nodo = onAllNodesWithTag("registrazioni-titolo-${REG_1.valore}", useUnmergedTree = true)
                    .fetchSemanticsNodes().firstOrNull()
                nodo?.config?.getOrNull(SemanticsProperties.EditableText)?.text == "Seduta"
            }

            campo.assertTextEquals("Seduta")
            onNodeWithTag("registrazioni-errore-riga-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(erroreSimulato).assertIsDisplayed()
        }

    @Test
    fun `Esc ripristina il titolo salvato senza sottomettere una rinomina`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            var chiamate = 0
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = listOf(rigaDiProva("Seduta"))),
                    azioni = AZIONI_VUOTE.copy(rinomina = { _, _ -> chiamate++ }),
                )
            }

            val campo = onNodeWithTag("registrazioni-titolo-${REG_1.valore}", useUnmergedTree = true)
            campo.performTextReplacement("Bozza non salvata")
            campo.assertTextEquals("Bozza non salvata")

            campo.performKeyInput { pressKey(Key.Escape) }

            campo.assertTextEquals("Seduta")
            assertEquals(0, chiamate, "Esc non deve mai sottomettere una rinomina")
        }

    private fun mutaRiga(
        stato: RegistrazioniUiStato,
        id: RegistrazioneId,
        f: (RigaRegistrazione) -> RigaRegistrazione,
    ): RegistrazioniUiStato {
        val dati = stato as RegistrazioniUiStato.Dati
        return dati.copy(righe = dati.righe.map { if (it.registrazioneId == id) f(it) else it })
    }
}
