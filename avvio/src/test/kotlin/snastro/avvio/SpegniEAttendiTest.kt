package snastro.avvio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L530e: each test uses its own throwaway [java.util.concurrent.ExecutorService] — never the shared
 * `eseguitoreRegistro` singleton ([SessioneProgettoImpl]'s own registry writer, reached only through
 * [attendiScritturaRegistro]): `shutdown()`ing THAT one here would leak into every other test sharing
 * this JVM (any later `SessioneProgettoImpl.crea`/`apri`/`chiudi` would find it rejecting new tasks).
 */
class SpegniEAttendiTest {
    @Test
    fun `L530e un compito gia in coda finisce prima del limite`() {
        val eseguitore = Executors.newSingleThreadExecutor()
        val fatto = CountDownLatch(1)
        eseguitore.execute { fatto.countDown() }

        val inTempo = spegniEAttendi(eseguitore, attesaMassimaMs = LIMITE_MS)

        assertTrue(inTempo)
        assertTrue(fatto.await(0, TimeUnit.MILLISECONDS), "il compito in coda non e' stato eseguito")
    }

    @Test
    fun `L530e un compito che non termina non blocca oltre il limite`() {
        val eseguitore = Executors.newSingleThreadExecutor()
        val maiSbloccato = CountDownLatch(1)
        eseguitore.execute { maiSbloccato.await() }
        val inizio = System.nanoTime()

        val inTempo = spegniEAttendi(eseguitore, attesaMassimaMs = LIMITE_MS)

        val trascorsiMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inizio)
        assertFalse(inTempo)
        assertTrue(trascorsiMs < LIMITE_MS + MARGINE_MS, "l'attesa e' durata $trascorsiMs ms")
        maiSbloccato.countDown()
    }

    private companion object {
        const val LIMITE_MS = 200L
        const val MARGINE_MS = 2_000L
    }
}
