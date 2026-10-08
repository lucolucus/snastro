package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.VoceId
import snastro.ui.Cambiamento
import snastro.ui.causatoDa
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CR-7 at the Voci panel's edges (ADR 0041 §3): a thrown card command and a failed panel read are logged at WARNING
 * with their cause; L269 (D-0065): an `Error` from a card command reaches the scope's handler and leaves no card
 * pending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatoVociGuastiTest {
    private val sfuggite = mutableListOf<Throwable>()

    private fun TestScope.ambiente() =
        AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))

    private fun TestScope.avvia(a: AmbienteVoci): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gestore = CoroutineExceptionHandler { _, e -> sfuggite += e }
        return a.presenter(CoroutineScope(SupervisorJob() + dispatcher + gestore), dispatcher)
    }

    private fun RegistrazionePresenter.carta(voce: VoceId): CartaVoce =
        assertNotNull((stato.value as? RegistrazioneUiStato.Dati)?.pannello).carte.single { it.voceId == voce }

    @Test
    fun `CR-7 un comando di card che lancia e registrato con la sua causa e la card mostra l errore generico`() =
        runTest {
            val a = ambiente()
            val presenter = avvia(a)
            advanceUntilIdle()
            val guasto = IllegalStateException("porta dei comandi rotta")
            a.guastoComando = guasto

            val registri = registriLogUi {
                presenter.azioni.conferma(V1)
                advanceUntilIdle()
            }

            val registrato = registri.single { it.causatoDa(guasto) }
            assertEquals(Level.WARNING, registrato.level)
            assertTrue("voce" in registrato.message, registrato.message)
            assertEquals(MESSAGGIO_ERRORE_GENERICO, presenter.carta(V1).errore)
            assertNull(presenter.carta(V1).inCorso)
        }

    @Test
    fun `CR-7 una lettura del pannello che fallisce e registrata con la sua causa`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.identificazioneRotta = true

        val registri = registriLogUi {
            a.aggiornamenti.emetti(Cambiamento(REG))
            advanceUntilIdle()
        }

        val registrato = registri.single { it.causatoDa(a.guastoLettura) }
        assertEquals(Level.WARNING, registrato.level)
        assertEquals(ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI), presenter.carta(V1).contenuto)
    }

    @Test
    fun `L269 un Error da un comando di card raggiunge il gestore e la card non resta in corso`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        val guasto = AssertionError("abbonato dopo-commit: errore di programmazione")
        a.guastoComando = guasto

        presenter.azioni.conferma(V1)
        advanceUntilIdle()

        assertTrue(sfuggite.any { e -> generateSequence(e) { it.cause }.any { it === guasto } }, "$sfuggite")
        assertNull(presenter.carta(V1).inCorso, "la card non resta in corso")
        assertTrue(presenter.carta(V1).azioniAbilitate)
    }
}
