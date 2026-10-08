package snastro.supporto

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** ADR 0041 §1: [catturaEccezione] catches an `Exception` only; cancellation and every `Error` propagate (D-0065). */
class CatturaEccezioneTest {
    @Test
    fun `un valore diventa Result success`() {
        assertEquals(Result.success(42), catturaEccezione { 42 })
    }

    @Test
    fun `RuntimeException e IOException diventano Result failure`() {
        listOf(RuntimeException("r"), IOException("io")).forEach { errore ->
            assertSame(errore, catturaEccezione<Int> { throw errore }.exceptionOrNull())
        }
    }

    @Test
    fun `CancellationException e rilanciata`() {
        val annullamento = CancellationException("c")
        val rilanciata = assertFailsWith<CancellationException> { catturaEccezione<Int> { throw annullamento } }
        assertSame(annullamento, rilanciata)
    }

    @Test
    fun `un Error come AssertionError si propaga`() {
        listOf(AssertionError("a"), OutOfMemoryError("oom")).forEach { errore ->
            assertSame(errore, assertFailsWith<Error> { catturaEccezione<Int> { throw errore } })
        }
    }
}
