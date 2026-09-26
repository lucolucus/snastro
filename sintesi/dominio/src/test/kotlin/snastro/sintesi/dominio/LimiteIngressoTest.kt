package snastro.sintesi.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimiteIngressoTest {
    @Test
    fun `AC-S2 stimaToken e il tetto di caratteri diviso 3 e il limite e 28000 token`() {
        assertEquals(0, LimiteIngresso.stimaToken(""))
        assertEquals(1, LimiteIngresso.stimaToken("a"))
        assertEquals(28_000, LimiteIngresso.stimaToken("a".repeat(84_000)))
        assertEquals(28_001, LimiteIngresso.stimaToken("a".repeat(84_001)))
        assertTrue(LimiteIngresso.stimaToken("a".repeat(84_000)) <= LimiteIngresso.LIMITE_TOKEN)
        assertTrue(LimiteIngresso.stimaToken("a".repeat(84_001)) > LimiteIngresso.LIMITE_TOKEN)
    }
}
