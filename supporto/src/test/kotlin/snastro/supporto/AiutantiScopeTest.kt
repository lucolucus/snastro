package snastro.supporto

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** AC-C9 [figlioDi], AC-C10 [gestoreErroriNonCatturati]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiutantiScopeTest {
    private val catturati = mutableListOf<Throwable>()
    private val gestore = CoroutineExceptionHandler { _, e -> catturati += e }

    private fun TestScope.genitore() = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

    @Test
    fun `AC-C9 un launch che fallisce nel figlio va al gestore e non cancella genitore ne fratelli`() = runTest {
        val genitore = genitore()
        val figlio = figlioDi(genitore, gestore = gestore)
        val altroFiglio = figlioDi(genitore, gestore = gestore)
        val fratello = figlio.launch { awaitCancellation() }
        val cugino = altroFiglio.launch { awaitCancellation() }
        val errore = IllegalStateException("guasto")

        figlio.launch { throw errore }
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(errore), catturati)
        assertTrue(genitore.coroutineContext.job.isActive)
        assertTrue(fratello.isActive)
        assertTrue(cugino.isActive)
        assertTrue(figlio.coroutineContext.job.isActive)
        genitore.cancel()
    }

    @Test
    fun `AC-C9 cancellare il genitore cancella il figlio`() = runTest {
        val genitore = genitore()
        val figlio = figlioDi(genitore, gestore = gestore)
        val lavoro = figlio.launch { awaitCancellation() }
        advanceUntilIdle()

        genitore.cancel()
        advanceUntilIdle()

        assertTrue(lavoro.isCancelled)
        assertFalse(figlio.coroutineContext.job.isActive)
    }

    @Test
    fun `AC-C9 senza dispatcher il figlio gira su quello del genitore, altrimenti sul dato`() = runTest {
        val suGenitore = StandardTestDispatcher(testScheduler)
        val dato = StandardTestDispatcher(testScheduler)
        val genitore = CoroutineScope(Job() + suGenitore)
        var visti = listOf<CoroutineContext.Element?>()

        figlioDi(genitore, gestore = gestore).launch { visti = visti + coroutineContext[ContinuationInterceptor] }
        figlioDi(genitore, dato, gestore).launch { visti = visti + coroutineContext[ContinuationInterceptor] }
        advanceUntilIdle()

        assertEquals(listOf<CoroutineContext.Element?>(suGenitore, dato), visti)
        genitore.cancel()
    }

    @Test
    fun `AC-C10 un eccezione che esce da un launch e segnalata una sola volta con quel throwable`() = runTest {
        val segnalazioni = SegnalazioniRegistrate()
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + gestoreErroriNonCatturati(segnalazioni),
        )
        val errore = IllegalStateException("sfuggito")

        scope.launch { throw errore }
        advanceUntilIdle()

        assertEquals(1, segnalazioni.tutte.size)
        assertSame(errore, segnalazioni.tutte.single().causa)
        scope.cancel()
    }
}
