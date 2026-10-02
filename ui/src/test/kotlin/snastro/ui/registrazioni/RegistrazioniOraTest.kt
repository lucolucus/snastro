package snastro.ui.registrazioni

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_DATA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_ORA_NON_VALIDA
import snastro.ui.testi.SUGGERIMENTO_ORA_SCONOSCIUTA
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val INC = IncontroId("incontro-1")
private val P1 = RegistrazioneId("parte-1")
private val P2 = RegistrazioneId("parte-2")
private val DATA = LocalDate.of(2026, 9, 30)

private val AZIONI = AzioniRegistrazioni(
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
    elimina = {},
    annullaElimina = {},
    confermaElimina = {},
    chiudiAvviso = {},
    aggiungiParti = { _, _, _ -> },
    scegliImporta = {},
    confermaImporta = {},
    annullaImporta = {},
    espandiIncontro = {},
    modificaOraDiInizio = { _, _ -> },
    modificaNumeroPersoneIncontro = { _, _ -> },
    avviaElaborazioniIncontro = {},
    chiudiErroreIncontro = {},
)

private fun parte(id: RegistrazioneId, n: Int, ora: LocalTime?) = RigaRegistrazione(
    registrazioneId = id,
    titolo = "file $n",
    dataRegistrazione = DATA,
    durataMs = 60_000,
    parte = ParteDiIncontro(n, "Riunione"),
    oraDiInizio = ora,
)

private fun statoEspanso(): RegistrazioniUiStato.Dati {
    val righe = listOf(parte(P1, 1, LocalTime.of(10, 0)), parte(P2, 2, null))
    return RegistrazioniUiStato.Dati(
        righe = righe,
        incontri = listOf(
            RigaIncontro(INC, "Riunione", DATA, 120_000, righe.map { it.registrazioneId }, espanso = true),
        ),
    )
}

/**
 * The S2 time field (AC-I68, L51/L98): the UI maps the typed text to a `LocalTime?` (`:avvio` builds the domain's
 * `OraDiInizio`, which stays strict), and the tooltip of an unknown time (AC-I68, L204).
 */
@OptIn(ExperimentalTestApi::class)
class RegistrazioniOraTest {
    @Test
    fun `L51 L98 vuoto o spazi cancellano l ora`() {
        assertNull("".aOra()!!.valore)
        assertNull("   ".aOra()!!.valore)
    }

    @Test
    fun `L51 L98 H mm e H mm con spazi sono accettati e portati a un orario`() {
        assertEquals(LocalTime.of(9, 5), "9:05".aOra()!!.valore)
        assertEquals(LocalTime.of(9, 5), " 09:05 ".aOra()!!.valore)
        assertEquals(LocalTime.of(23, 59), "23:59".aOra()!!.valore)
        assertEquals(LocalTime.MIDNIGHT, "0:00".aOra()!!.valore)
    }

    @Test
    fun `L51 L98 tutto il resto non e un orario`() {
        listOf("24:00", "12:60", "9:5", "09:05:30", "abc", "0905", "-1:00", "9.05").forEach {
            assertNull(it.aOra(), it)
        }
    }

    @Test
    fun `L51 L98 il messaggio di ora non valida nomina il formato`() {
        assertTrue("HH:mm" in MESSAGGIO_ORA_NON_VALIDA)
    }

    @Test
    fun `L51 L98 un testo non valido nel campo ora mostra il messaggio e non invia nulla`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val inviate = mutableListOf<Pair<RegistrazioneId, LocalTime?>>()
            setContent {
                SchermataRegistrazioni(
                    statoEspanso(),
                    AZIONI.copy(modificaOraDiInizio = { id, ora -> inviate += id to ora }),
                )
            }
            val campo = onNodeWithTag("registrazioni-ora-${P1.valore}", useUnmergedTree = true)
            campo.performClick()
            campo.performTextReplacement("25:00")
            campo.performImeAction()

            onNodeWithTag("registrazioni-ora-errore-${P1.valore}", useUnmergedTree = true)
                .assertTextEquals(MESSAGGIO_ORA_NON_VALIDA)
            assertTrue(inviate.isEmpty())
        }

    @Test
    fun `L51 L98 H mm digitato nel campo ora invia l orario riempito`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val inviate = mutableListOf<Pair<RegistrazioneId, LocalTime?>>()
            setContent {
                SchermataRegistrazioni(
                    statoEspanso(),
                    AZIONI.copy(modificaOraDiInizio = { id, ora -> inviate += id to ora }),
                )
            }
            val campo = onNodeWithTag("registrazioni-ora-${P2.valore}", useUnmergedTree = true)
            campo.performClick()
            campo.performTextReplacement(" 9:05 ")
            campo.performImeAction()

            assertEquals(listOf(P2 to LocalTime.of(9, 5)), inviate)
        }

    @Test
    fun `L204 AC-I68 il suggerimento compare sull ora sconosciuta e non su quella nota`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent { SchermataRegistrazioni(statoEspanso(), AZIONI) }
            mainClock.autoAdvance = false

            onNodeWithTag("registrazioni-ora-${P1.valore}", useUnmergedTree = true)
                .performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(1_500)
            assertEquals(0, onAllNodesWithText(SUGGERIMENTO_ORA_SCONOSCIUTA).fetchSemanticsNodes().size)

            onNodeWithTag("registrazioni-ora-${P2.valore}", useUnmergedTree = true)
                .performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(1_500)
            assertEquals(1, onAllNodesWithText(SUGGERIMENTO_ORA_SCONOSCIUTA).fetchSemanticsNodes().size)
        }

    @Test
    fun `L205 un testo non valido digitato resta nel campo quando lo si riapre`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SchermataRegistrazioni(
                    RegistrazioniUiStato.Dati(righe = listOf(parte(P1, 1, null).copy(parte = null))),
                    AZIONI,
                )
            }
            val campo = onNodeWithTag("registrazioni-data-${P1.valore}", useUnmergedTree = true)
            campo.performClick()
            campo.performTextReplacement("31/02/2026")
            campo.performImeAction()
            onNodeWithTag("registrazioni-data-errore-${P1.valore}", useUnmergedTree = true)
                .assertTextEquals(MESSAGGIO_DATA_NON_VALIDA)

            campo.performClick()

            campo.assertTextEquals("31/02/2026")
        }
}
