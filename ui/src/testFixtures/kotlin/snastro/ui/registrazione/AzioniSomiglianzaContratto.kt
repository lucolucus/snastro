package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [AzioniSomiglianza] (boundary `ui-azioni-somiglianza`, ADR 0019 Amendment
 * (b).2): the fake proves it green on its own (D1); `avvio-parlanti`'s per-project glue subclasses it
 * (D2). [con] builds the port in [progetto] (the project scope) whose computation plans [scenario]'s
 * groups and uncertain count; [SondaSomiglianza.applicazioni] counts the plans actually applied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AzioniSomiglianzaContratto {
    /** What the computation finds: [gruppi] ordered by (a, da), [incerte]. */
    class ScenarioSomiglianza(val gruppi: List<GruppoSpostamenti>, val incerte: Int)

    class SondaSomiglianza(val porta: AzioniSomiglianza, val applicazioni: () -> Int)

    protected abstract fun con(progetto: CoroutineScope, clock: Clock, scenario: ScenarioSomiglianza): SondaSomiglianza

    private val id = RegistrazioneId("id-1")
    private val orologio = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)
    private val gruppi = listOf(
        GruppoSpostamenti(VoceId(3), VoceId(1), 2),
        GruppoSpostamenti(VoceId(4), VoceId(1), 1),
        GruppoSpostamenti(VoceId(3), VoceId(2), 1),
    )

    // Not `backgroundScope`: `advanceUntilIdle` does not run background-only work (kotlinx-coroutines-test).
    private fun TestScope.progetto(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

    private fun TestScope.sonda(g: List<GruppoSpostamenti> = gruppi) = con(progetto(), orologio, ScenarioSomiglianza(g, 1))

    @Test
    fun `calcola finisce in Anteprima con i gruppi e le incerte, senza applicare nulla`() = runTest {
        val s = sonda()
        s.porta.calcola(id)
        advanceUntilIdle()
        assertEquals(StatoSomiglianza.Anteprima(gruppi, 1), s.porta.stato.value[id])
        assertEquals(0, s.applicazioni())
    }

    @Test
    fun `applica invia il piano tenuto una sola volta e finisce in Esito`() = runTest {
        val s = sonda()
        s.porta.calcola(id)
        advanceUntilIdle()
        s.porta.applica(id)
        s.porta.applica(id)
        advanceUntilIdle()
        assertEquals(StatoSomiglianza.Esito(4, 1), s.porta.stato.value[id])
        assertEquals(1, s.applicazioni())
    }

    @Test
    fun `applica senza anteprima o con N zero non invia nulla`() = runTest {
        val s = sonda(emptyList())
        s.porta.applica(id)
        advanceUntilIdle()
        assertNull(s.porta.stato.value[id])
        s.porta.calcola(id)
        advanceUntilIdle()
        s.porta.applica(id)
        advanceUntilIdle()
        assertIs<StatoSomiglianza.Anteprima>(s.porta.stato.value[id])
        assertEquals(0, s.applicazioni())
    }

    @Test
    fun `annulla sull anteprima scarta il piano e applica poi non invia nulla`() = runTest {
        val s = sonda()
        s.porta.calcola(id)
        advanceUntilIdle()
        s.porta.annulla(id)
        advanceUntilIdle()
        assertNull(s.porta.stato.value[id])
        s.porta.applica(id)
        advanceUntilIdle()
        assertEquals(0, s.applicazioni())
    }

    @Test
    fun `annulla su un Esito lo toglie`() = runTest {
        val s = sonda()
        s.porta.calcola(id)
        advanceUntilIdle()
        s.porta.applica(id)
        advanceUntilIdle()
        s.porta.annulla(id)
        advanceUntilIdle()
        assertNull(s.porta.stato.value[id])
    }
}
