package snastro.parlanti.applicazione.porte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoglieFasciaTest {
    @Test
    fun `AC-41 SoglieFascia con forte minore o uguale a debole sono rifiutate`() {
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = 0.5, debole = 0.7) }
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = 0.6, debole = 0.6) }
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = Double.NaN, debole = 0.5) }
    }

    @Test
    fun `AC-41 SoglieFascia con forte maggiore di debole sono accettate`() {
        val soglie = SoglieFascia(forte = 0.7, debole = 0.5)

        assertEquals(0.7, soglie.forte)
        assertEquals(0.5, soglie.debole)
    }
}
