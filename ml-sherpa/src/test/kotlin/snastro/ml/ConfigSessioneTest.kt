package snastro.ml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigSessioneTest {
    @Test
    fun `AC-400 il provider predefinito e cpu`() {
        assertEquals("cpu", ConfigSessione(percorsiModello = emptyList(), threadIntraOp = 6).provider)
    }

    @Test
    fun `threadIntraOp deve essere almeno 1`() {
        assertFailsWith<IllegalArgumentException> { ConfigSessione(emptyList(), threadIntraOp = 0) }
    }

    @Test
    fun `i core di prestazione dell host sono almeno 1 e non piu dei processori`() {
        val core = ConfigSessione.coreDiPrestazione()

        assertTrue(core in 1..Runtime.getRuntime().availableProcessors(), "core = $core")
    }
}
