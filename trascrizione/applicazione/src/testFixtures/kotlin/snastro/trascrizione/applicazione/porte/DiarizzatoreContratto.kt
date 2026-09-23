package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.atteso
import snastro.trascrizione.dominio.NumeroPersone
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [Diarizzatore] (boundary `tec-diarizzatore`, ADR 0004): every Turno has
 * `inizio < fine` within the duration and `voceIndice >= 0`; speech gives turns; empty samples and digital
 * silence give none, without throwing; with a Numero di persone k the Turni have at most k distinct `voceIndice`
 * (fewer is allowed; a k the audio cannot support never throws), without one the clustering is automatic (AC-32,
 * AC-374, ADR 0014). One subclass per implementation; the real one is `@Tag("modelli")`.
 */
public abstract class DiarizzatoreContratto {
    /** The diarizer under test. */
    protected abstract fun diarizzatore(): Diarizzatore

    /** Speech the implementation diarizes: a synthetic tone for the Finta, a `sample/` excerpt for real models. */
    protected open fun parlato(): CampioniAudio = tonoDiProva(DURATA_PARLATO_MS)

    @Test
    public fun `AC-32 ogni Turno ha inizio prima di fine entro la durata e voceIndice non negativo`() {
        val campioni = parlato()

        val turni = diarizzatore().diarizza(campioni, numeroPersone = null)

        assertTrue(turni.isNotEmpty(), "il parlato produce almeno un Turno")
        assertTurniValidi(turni, campioni)
    }

    @Test
    public fun `AC-32 campioni vuoti non producono Turni`() {
        assertEquals(emptyList(), diarizzatore().diarizza(CampioniAudio(FloatArray(0)), numeroPersone = null))
    }

    @Test
    public fun `AC-32 il silenzio non produce Turni`() {
        assertEquals(emptyList(), diarizzatore().diarizza(silenzio(DURATA_PARLATO_MS), numeroPersone = null))
    }

    @Test
    public fun `AC-374 con numeroPersone k i Turni hanno al piu k voceIndice distinti`() {
        val campioni = parlato()

        listOf(1, 2, 10).forEach { k ->
            val turni = diarizzatore().diarizza(campioni, NumeroPersone.di(k).atteso())

            assertTrue(turni.isNotEmpty(), "k = $k: il parlato produce almeno un Turno")
            assertTurniValidi(turni, campioni)
            val voci = turni.map { it.voceIndice }.toSet()
            assertTrue(voci.size <= k, "k = $k ma ${voci.size} voceIndice distinti: $voci")
        }
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
