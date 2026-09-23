package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IntervalloMsTest {
    @Test
    fun `AC-5 IntervalloMs rifiuta inizio uguale a fine`() {
        assertFailsWith<IllegalArgumentException> { IntervalloMs(100, 100) }
    }

    @Test
    fun `AC-5 IntervalloMs rifiuta inizio maggiore di fine`() {
        assertFailsWith<IllegalArgumentException> { IntervalloMs(200, 100) }
    }

    @Test
    fun `AC-5 IntervalloMs rifiuta inizio negativo`() {
        assertFailsWith<IllegalArgumentException> { IntervalloMs(-1, 100) }
    }

    @Test
    fun `AC-5 IntervalloMs accetta 0 minore o uguale a inizio minore di fine`() {
        assertEquals(0L, IntervalloMs(0, 1).inizioMs)
    }

    @Test
    fun `gli intervalli sono ordinati numericamente per inizio poi per fine`() {
        val ordinati = listOf(IntervalloMs(900, 1000), IntervalloMs(10, 50), IntervalloMs(10, 20)).sorted()
        assertEquals(listOf(IntervalloMs(10, 20), IntervalloMs(10, 50), IntervalloMs(900, 1000)), ordinati)
    }
}
