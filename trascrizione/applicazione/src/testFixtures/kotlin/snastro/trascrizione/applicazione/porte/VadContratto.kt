package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [Vad] (boundary `tec-vad`, ADR 0004): intervals ordered, non-overlapping,
 * within the duration; empty samples and digital silence give none, without throwing. One subclass per
 * implementation; the real one is `@Tag("modelli")`.
 */
public abstract class VadContratto {
    /** The detector under test. */
    protected abstract fun vad(): Vad

    /** Speech the implementation detects: a synthetic tone for the Finta, a `sample/` excerpt for real models. */
    protected open fun parlato(): CampioniAudio = tonoDiProva(DURATA_PARLATO_MS)

    @Test
    public fun `AC-34 intervalli ordinati non sovrapposti entro la durata`() {
        val campioni = parlato()

        val intervalli = vad().parlato(campioni)

        assertTrue(intervalli.isNotEmpty(), "il parlato produce almeno un intervallo")
        assertValidi(intervalli, campioni)
    }

    @Test
    public fun `AC-34 parlato intervallato da silenzio resta ordinato e non sovrapposto`() {
        val voce = parlato().campioni
        val pausa = silenzio(PAUSA_MS).campioni
        val campioni = CampioniAudio(pausa + voce + pausa + voce + pausa)

        val intervalli = vad().parlato(campioni)

        assertTrue(intervalli.isNotEmpty(), "il parlato produce almeno un intervallo")
        assertValidi(intervalli, campioni)
    }

    @Test
    public fun `AC-34 campioni vuoti non producono intervalli`() {
        assertEquals(emptyList(), vad().parlato(CampioniAudio(FloatArray(0))))
    }

    @Test
    public fun `AC-34 il silenzio non produce intervalli`() {
        assertEquals(emptyList(), vad().parlato(silenzio(DURATA_PARLATO_MS)))
    }

    private fun assertValidi(intervalli: List<IntervalloMs>, campioni: CampioniAudio) {
        val durata = campioni.durataMs()
        assertTrue(intervalli.last().fineMs <= durata, "${intervalli.last()} oltre $durata ms")
        intervalli.zipWithNext().forEach { (a, b) -> assertTrue(a.fineMs <= b.inizioMs, "$a si sovrappone a $b") }
    }

    private companion object {
        const val DURATA_PARLATO_MS = 3_000L
        const val PAUSA_MS = 1_000L
    }
}
