package snastro.sintesi.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimiteIngressoTest {
    @Test
    fun `AC-S2 stimaToken e il tetto di 5 caratteri per 12 e il limite e 28000 token`() {
        assertEquals(0, LimiteIngresso.stimaToken(""))
        assertEquals(1, LimiteIngresso.stimaToken("a"))
        assertEquals(1, LimiteIngresso.stimaToken("ab"))
        assertEquals(2, LimiteIngresso.stimaToken("abc"))
        assertEquals(5, LimiteIngresso.stimaToken("a".repeat(12)))
        assertEquals(28_000, LimiteIngresso.stimaToken("a".repeat(67_200)))
        assertEquals(28_001, LimiteIngresso.stimaToken("a".repeat(67_201)))
        assertTrue(LimiteIngresso.stimaToken("a".repeat(67_200)) <= LimiteIngresso.LIMITE_TOKEN)
        assertTrue(LimiteIngresso.stimaToken("a".repeat(67_201)) > LimiteIngresso.LIMITE_TOKEN)
    }

    @Test
    fun `A59 al punto di overflow di 5 per lunghezza in Int la stima resta positiva e sopra il limite`() {
        // Minimal repro, on the length alone (no ~430 MB string allocated): 5 * length overflows
        // Int.MAX_VALUE (2_147_483_647) starting exactly here (5 * 429_496_730 = 2_147_483_650).
        // Pre-fix (Int arithmetic) this wrapped negative and would have let valuta() pass it.
        val lunghezzaMinimaDiOverflow = 429_496_730

        val stima = LimiteIngresso.stimaTokenDiLunghezza(lunghezzaMinimaDiOverflow)

        assertTrue(stima > 0, "la stima non deve diventare negativa per overflow")
        assertTrue(stima > LimiteIngresso.LIMITE_TOKEN, "una stima corretta supera comunque il limite")
    }
}
