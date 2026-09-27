package snastro.supporto

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** AC-C11: [catturaNonFatale] catches the non-fatal, rethrows cancellation and VM errors. */
class CatturaNonFataleTest {
    @Test
    fun `AC-C11 un valore diventa Result success`() {
        assertEquals(Result.success(42), catturaNonFatale { 42 })
    }

    @Test
    fun `AC-C11 RuntimeException e IOException diventano Result failure`() {
        listOf(RuntimeException("r"), IOException("io")).forEach { errore ->
            assertSame(errore, catturaNonFatale<Int> { throw errore }.exceptionOrNull())
        }
    }

    @Test
    fun `AC-C11 CancellationException OutOfMemoryError e StackOverflowError sono rilanciate`() {
        listOf(CancellationException("c"), OutOfMemoryError("oom"), StackOverflowError("so")).forEach { errore ->
            assertSame(errore, assertFailsWith<Throwable> { catturaNonFatale<Int> { throw errore } })
        }
    }
}
