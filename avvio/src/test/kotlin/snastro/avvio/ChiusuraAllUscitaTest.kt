package snastro.avvio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** fix-batch-16 LOW-3: closing the window closes the open project first, never hanging the exit. */
class ChiusuraAllUscitaTest {
    @Test
    fun `fix-batch-16 LOW-3 all uscita il progetto aperto viene chiuso`() {
        val chiuso = CountDownLatch(1)

        val inTempo = chiudiPrimaDiUscire({ chiuso.countDown() })

        assertTrue(inTempo)
        assertTrue(chiuso.await(0, TimeUnit.MILLISECONDS), "chiudi non e' stato chiamato")
    }

    @Test
    fun `fix-batch-16 LOW-3 una chiusura che non termina non blocca l uscita oltre il limite`() {
        val maiSbloccato = CountDownLatch(1)
        val inizio = System.nanoTime()

        val inTempo = chiudiPrimaDiUscire({ maiSbloccato.await() }, attesaMassimaMs = LIMITE_MS)

        val trascorsiMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inizio)
        assertFalse(inTempo)
        assertTrue(trascorsiMs < LIMITE_MS + MARGINE_MS, "l'uscita ha atteso $trascorsiMs ms")
        maiSbloccato.countDown()
    }

    private companion object {
        const val LIMITE_MS = 200L
        const val MARGINE_MS = 2_000L
    }
}
