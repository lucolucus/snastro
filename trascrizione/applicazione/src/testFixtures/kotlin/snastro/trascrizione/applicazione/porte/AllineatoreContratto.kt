package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [Allineatore] (boundary `tec-allineatore`, ADR 0004, INV-7, Q-4): every
 * SegmentoGrezzo has `inizio < fine` within the duration, a `voceIndice` of the given turns and overlaps in
 * time a Turno of that same voice (no swapped voices, no shifted turns); overlaps
 * between turns are neither trimmed nor dropped; no turns give no segments; silence never throws.
 * Implementation-agnostic (strategy A or B). One subclass per implementation — the pure Kotlin one with
 * the Finte of [RiconoscitoreParlato] and [Vad] runs in the gate; a real-model one is `@Tag("modelli")`.
 */
public abstract class AllineatoreContratto {
    /** The aligner under test. */
    protected abstract fun allineatore(): Allineatore

    /** At least [DURATA_MINIMA_MS] of continuous speech: a synthetic tone, or a `sample/` excerpt for real models. */
    protected open fun parlato(): CampioniAudio = tonoDiProva(DURATA_MINIMA_MS)

    @Test
    public fun `AC-35 ogni SegmentoGrezzo ha inizio prima di fine entro la durata e un voceIndice dei turni`() {
        val campioni = parlato()

        val segmenti = allineatore().allinea(campioni, TURNI)

        assertTrue(segmenti.isNotEmpty(), "il parlato con turni produce segmenti")
        assertValidi(segmenti, campioni)
    }

    @Test
    public fun `AC-35 le sovrapposizioni tra turni non vengono tagliate ne eliminate`() {
        val segmenti = allineatore().allinea(parlato(), TURNI)

        val voce0 = segmenti.filter { it.voceIndice == 0 }
        val voce1 = segmenti.filter { it.voceIndice == 1 }
        assertTrue(voce0.isNotEmpty() && voce1.isNotEmpty(), "nessuna voce sovrapposta e eliminata: $segmenti")
        assertTrue(
            voce0.any { a -> voce1.any { b -> a.intervallo.sovrapposto(b.intervallo) } },
            "i segmenti delle due voci si sovrappongono ancora: $segmenti",
        )
    }

    @Test
    public fun `AC-35 senza turni non ci sono segmenti`() {
        assertEquals(emptyList(), allineatore().allinea(parlato(), emptyList()))
        assertEquals(emptyList(), allineatore().allinea(CampioniAudio(FloatArray(0)), emptyList()))
    }

    @Test
    public fun `AC-35 il silenzio con turni non lancia e i segmenti restano validi`() {
        val campioni = silenzio(DURATA_MINIMA_MS)

        assertValidi(allineatore().allinea(campioni, TURNI), campioni)
    }

    private fun assertValidi(segmenti: List<SegmentoGrezzo>, campioni: CampioniAudio) {
        val voci = TURNI.map { it.voceIndice }.toSet()
        segmenti.forEach {
            assertTrue(it.intervallo.fineMs <= campioni.durataMs(), "$it oltre ${campioni.durataMs()} ms")
            assertTrue(it.voceIndice in voci, "$it: voceIndice non presente nei turni")
            assertTrue(
                TURNI.any { t -> t.voceIndice == it.voceIndice && t.intervallo.sovrapposto(it.intervallo) },
                "$it non si sovrappone ad alcun Turno della sua voce",
            )
        }
    }

    private fun IntervalloMs.sovrapposto(altro: IntervalloMs): Boolean =
        inizioMs < altro.fineMs && altro.inizioMs < fineMs

    public companion object {
        /** Minimum length of [parlato]: the contract's turns span 6 000 ms. */
        public const val DURATA_MINIMA_MS: Long = 6_000

        /** Voce 1 overlaps voce 0 in [2 000, 3 000) ms. */
        private val TURNI = listOf(
            Turno(IntervalloMs(0, 3_000), voceIndice = 0),
            Turno(IntervalloMs(2_000, 5_000), voceIndice = 1),
            Turno(IntervalloMs(5_000, 6_000), voceIndice = 0),
        )
    }
}
