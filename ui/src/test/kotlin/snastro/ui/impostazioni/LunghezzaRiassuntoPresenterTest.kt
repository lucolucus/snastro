package snastro.ui.impostazioni

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.dominio.ErroreSintesi
import snastro.ui.causatoDa
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.erroreLunghezzaMassima
import snastro.ui.testi.messaggioPer
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LunghezzaRiassuntoPresenterTest {
    private var valore = 2000
    private val modifiche = mutableListOf<Int>()
    private var esitoModifica: Esito<Unit> = Esito.Ok(Unit)
    private var guastoLettura: Throwable? = null
    private var guastoModifica: Throwable? = null

    private fun TestScope.presenter(scope: CoroutineScope = this) = LunghezzaRiassuntoPresenter(
        scope = scope,
        io = StandardTestDispatcher(testScheduler),
        leggi = {
            guastoLettura?.let { throw it }
            ImpostazioniSintesiVista(valore, minimo = 300, massimo = 2500)
        },
        modifica = { parole ->
            modifiche += parole
            guastoModifica?.let { throw it }
            esitoModifica.also { if (it is Esito.Ok) valore = parole }
        },
    )

    private fun LunghezzaRiassuntoPresenter.dati() = assertIs<LunghezzaRiassuntoUiStato.Dati>(stato.value)

    @Test
    fun `mostra il valore del progetto`() = runTest {
        val p = presenter()
        advanceUntilIdle()

        assertEquals("2000", p.dati().testo)
        assertFalse(p.dati().modificata)
    }

    @Test
    fun `Salva un valore nell intervallo lo salva e conferma`() = runTest {
        val p = presenter()
        advanceUntilIdle()

        p.azioni.cambia("1200")
        assertTrue(p.dati().modificata)
        p.azioni.salva()
        advanceUntilIdle()

        assertEquals(listOf(1200), modifiche)
        assertEquals(1200, p.dati().salvata)
        assertTrue(p.dati().conferma)
        assertFalse(p.dati().modificata)
    }

    @Test
    fun `un valore fuori intervallo non viene salvato`() = runTest {
        val p = presenter()
        advanceUntilIdle()

        p.azioni.cambia("9000")
        p.azioni.salva()
        advanceUntilIdle()

        assertEquals(emptyList(), modifiche)
        assertEquals(erroreLunghezzaMassima(300, 2500), p.dati().errore)
    }

    @Test
    fun `l errore del comando e mostrato cosi com e`() = runTest {
        esitoModifica = Esito.Errore(ErroreSintesi.ModelloNonInstallato)
        val p = presenter()
        advanceUntilIdle()

        p.azioni.cambia("800")
        p.azioni.salva()
        advanceUntilIdle()

        assertEquals(messaggioPer(ErroreSintesi.ModelloNonInstallato), p.dati().errore)
        assertFalse(p.dati().inCorso)
    }

    @Test
    fun `Annulla torna al valore salvato`() = runTest {
        val p = presenter()
        advanceUntilIdle()

        p.azioni.cambia("abc")
        p.azioni.ripristina()

        assertEquals("2000", p.dati().testo)
    }

    @Test
    fun `CR-7 una lettura che lancia e registrata con la sua causa e mostra l errore generico`() = runTest {
        val guasto = IllegalStateException("impostazioni illeggibili")
        guastoLettura = guasto

        lateinit var p: LunghezzaRiassuntoPresenter
        val registri = registriLogUi {
            p = presenter()
            advanceUntilIdle()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        assertEquals(LunghezzaRiassuntoUiStato.Errore(MESSAGGIO_ERRORE_GENERICO), p.stato.value)
    }

    @Test
    fun `CR-7 una modifica che lancia e registrata con la sua causa e mostra l errore generico`() = runTest {
        val guasto = IllegalStateException("guasto SQL")
        guastoModifica = guasto
        val p = presenter()
        advanceUntilIdle()

        p.azioni.cambia("800")
        val registri = registriLogUi {
            p.azioni.salva()
            advanceUntilIdle()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, p.dati().errore)
        assertFalse(p.dati().inCorso)
        assertEquals(2000, p.dati().salvata)
    }

    @Test
    fun `D-0065 un Error della modifica si propaga`() = runTest {
        val sfuggite = mutableListOf<Throwable>()
        val gestore = CoroutineExceptionHandler { _, e -> sfuggite += e }
        val p = presenter(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + gestore))
        advanceUntilIdle()
        val guasto = AssertionError("errore di programmazione dopo il commit")
        guastoModifica = guasto

        p.azioni.cambia("800")
        p.azioni.salva()
        advanceUntilIdle()

        assertTrue(sfuggite.any { e -> generateSequence(e) { it.cause }.any { it === guasto } }, "$sfuggite")
        assertFalse(p.dati().inCorso)
    }
}
