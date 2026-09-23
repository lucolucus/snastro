package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contract of [GeneratoreId]: one subclass per implementation. */
public abstract class GeneratoreIdContratto {
    protected abstract fun generatore(): GeneratoreId

    @Test
    public fun `ogni id generato e non vuoto`() {
        val generatore = generatore()
        repeat(RIPETIZIONI) { assertTrue(generatore.nuovo().isNotBlank()) }
    }

    @Test
    public fun `gli id generati sono tutti distinti`() {
        val generatore = generatore()
        val id = List(RIPETIZIONI) { generatore.nuovo() }
        assertEquals(RIPETIZIONI, id.toSet().size)
    }

    private companion object {
        const val RIPETIZIONI = 100
    }
}
