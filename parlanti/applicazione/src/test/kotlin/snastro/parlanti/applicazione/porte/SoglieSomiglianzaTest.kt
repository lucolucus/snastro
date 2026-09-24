package snastro.parlanti.applicazione.porte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoglieSomiglianzaTest {
    @Test
    fun `AC-500 margine minore o uguale a zero o NaN e rifiutato`() {
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = 0.3, margine = 0.0) }
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = 0.3, margine = -0.1) }
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = 0.3, margine = Double.NaN) }
    }

    @Test
    fun `AC-500 minima fuori da -1_0 1_0 o NaN e rifiutata`() {
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = 1.1, margine = 0.05) }
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = -1.1, margine = 0.05) }
        assertFailsWith<IllegalArgumentException> { SoglieSomiglianza(minima = Double.NaN, margine = 0.05) }
    }

    @Test
    fun `AC-500 valori validi sono accettati inclusi i bordi -1_0 e 1_0`() {
        assertEquals(0.30, SoglieSomiglianza(minima = 0.30, margine = 0.05).minima)
        assertEquals(0.05, SoglieSomiglianza(minima = 0.30, margine = 0.05).margine)
        SoglieSomiglianza(minima = -1.0, margine = 0.05)
        SoglieSomiglianza(minima = 1.0, margine = 0.05)
    }
}
