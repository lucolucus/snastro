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
}
