package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [Diarizzatore] (boundary `tec-diarizzatore`, ADR 0004): every Turno has
 * `inizio < fine` within the duration and `voceIndice >= 0`; speech gives turns; empty samples and digital
 * silence give none, without throwing. One subclass per implementation; the real one is `@Tag("modelli")`.
 */
public abstract class DiarizzatoreContratto {
    /** The diarizer under test. */
    protected abstract fun diarizzatore(): Diarizzatore

    /** Speech the implementation diarizes: a synthetic tone for the Finta, a `sample/` excerpt for real models. */
    protected open fun parlato(): CampioniAudio = tonoDiProva(DURATA_PARLATO_MS)

    @Test
    public fun `AC-32 ogni Turno ha inizio prima di fine entro la durata e voceIndice non negativo`() {
        val campioni = parlato()

        val turni = diarizzatore().diarizza(campioni)

        assertTrue(turni.isNotEmpty(), "il parlato produce almeno un Turno")
        assertTurniValidi(turni, campioni)
    }

    @Test
    public fun `AC-32 campioni vuoti non producono Turni`() {
        assertEquals(emptyList(), diarizzatore().diarizza(CampioniAudio(FloatArray(0))))
    }

    @Test
    public fun `AC-32 il silenzio non produce Turni`() {
        assertEquals(emptyList(), diarizzatore().diarizza(silenzio(DURATA_PARLATO_MS)))
    }

    private fun assertTurniValidi(turni: List<Turno>, campioni: CampioniAudio) {
        turni.forEach {
            assertTrue(it.intervallo.inizioMs < it.intervallo.fineMs, "$it")
            assertTrue(it.intervallo.fineMs <= campioni.durataMs(), "$it oltre ${campioni.durataMs()} ms")
            assertTrue(it.voceIndice >= 0, "$it")
        }
    }

    private companion object {
        const val DURATA_PARLATO_MS = 3_000L
    }
}
