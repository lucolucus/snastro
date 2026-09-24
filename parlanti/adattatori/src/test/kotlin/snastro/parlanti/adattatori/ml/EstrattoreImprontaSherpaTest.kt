package snastro.parlanti.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.ml.ConfigSessione
import snastro.ml.ModelloEmbeddingFinto
import snastro.ml.MotoreSherpa
import snastro.ml.motoreSherpaSenzaNativi
import snastro.ml.sessioni
import snastro.parlanti.dominio.Impronta
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * [EstrattoreImprontaSherpa] on the REAL Mutex and sessions of a native-free `MotoreSherpa` and a fake
 * TitaNet model (gate: no native library, no model is ever loaded).
 */
class EstrattoreImprontaSherpaTest {
    private val modello = ModelloEmbeddingFinto { campioni -> floatArrayOf(campioni.sum(), 1f) }
    private val motore = motoreSherpaSenzaNativi()
    private val campioni = CampioniAudio(FloatArray(CAMPIONI) { 0.5f })

    private fun estrattore(m: ModelloEmbeddingFinto = modello, su: MotoreSherpa = motore) =
        EstrattoreImprontaSherpa(m.su(su))

    @Test
    fun `AC-310 modello e embedding-nemo-titanet-small, costante per l istanza`() {
        val e = estrattore()

        val prima = e.modello
        e.estrai(campioni)

        assertEquals("embedding-nemo-titanet-small", prima)
        assertEquals(prima, e.modello)
    }

    @Test
    fun `AC-406 AC-492 N estrai caricano il modello una volta e aprono UNA conSessione per estrai`() {
        val e = estrattore()

        val impronte = List(N_ESTRAZIONI) { e.estrai(campioni) }

        assertEquals(1, modello.caricamenti)
        assertEquals(N_ESTRAZIONI, motore.sessioni)
        assertEquals(Impronta(floatArrayOf(CAMPIONI * 0.5f, 1f)), impronte.last())
    }

    @Test
    fun `AC-406 dopo il ritorno di estrai una conSessione da un altro thread ottiene subito il Mutex`() {
        val e = estrattore()
        e.estrai(campioni)

        assertEquals("libero", suAltroThread { motore.conSessione(ConfigSessione(emptyList(), 1)) { "libero" } })
    }

    @Test
    fun `AC-311 il Mutex e preso dentro estrai e rilasciato anche su eccezione`() {
        var tenutoDentro = false
        val guasto = ModelloEmbeddingFinto {
            tenutoDentro = suAltroThread { tentaMutex() } == false
            error("guasto nativo")
        }
        val e = estrattore(guasto)

        assertFailsWith<IllegalStateException> { e.estrai(campioni) }

        assertEquals(true, tenutoDentro, "durante la chiamata nativa il Mutex e tenuto")
        assertEquals(true, suAltroThread { tentaMutex() }, "dopo l eccezione il Mutex e libero")
    }

    @Test
    fun `AC-407 un interrupt durante la chiamata nativa da InterruptedException a sessione chiusa, nessuna Impronta`() {
        val interrottoDurante = ModelloEmbeddingFinto {
            Thread.currentThread().interrupt() // the cancellation arrives while the native call runs
            floatArrayOf(1f, 1f)
        }
        val e = estrattore(interrottoDurante)

        assertFailsWith<InterruptedException> { e.estrai(campioni) }

        assertFalse(Thread.currentThread().isInterrupted, "l'interrupt e consumato dall'eccezione")
        assertEquals(true, suAltroThread { tentaMutex() }, "la sessione e chiusa e il Mutex libero")
    }

    @Test
    fun `AC-492 AC-260 chiudi rilascia il modello e l estrai successivo lo ricarica`() {
        val e = estrattore()

        e.estrai(campioni)
        e.chiudi()
        e.chiudi()
        e.estrai(campioni)

        assertEquals(1, modello.rilasci)
        assertEquals(2, modello.caricamenti)
    }

    /** `true` iff a session opens within [ATTESA_BREVE_MS] from another thread. */
    private fun tentaMutex(): Boolean {
        val altro = Executors.newSingleThreadExecutor()
        return try {
            val f = altro.submit<Unit> { motore.conSessione(ConfigSessione(emptyList(), 1)) { } }
            try {
                f.get(ATTESA_BREVE_MS, TimeUnit.MILLISECONDS)
                true
            } catch (_: java.util.concurrent.TimeoutException) {
                f.cancel(true)
                false
            }
        } finally {
            altro.shutdownNow()
        }
    }

    private fun <T> suAltroThread(blocco: () -> T): T {
        val altro = Executors.newSingleThreadExecutor()
        return try {
            altro.submit<T> { blocco() }.get(ATTESA_S, TimeUnit.SECONDS)
        } finally {
            altro.shutdownNow()
        }
    }

    private companion object {
        const val CAMPIONI = 16_000
        const val N_ESTRAZIONI = 4
        const val ATTESA_S = 10L
        const val ATTESA_BREVE_MS = 300L
    }
}
