package snastro.ml

import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** AC-491 (gate, fake model): the [EmbeddingSherpa] lifecycle — no native library or model is loaded. */
class EmbeddingSherpaTest {
    private val modello = ModelloEmbeddingFinto { campioni -> floatArrayOf(campioni.sum(), campioni.size.toFloat()) }

    @Test
    fun `AC-491 N calcola caricano il modello una volta e aprono una conSessione per chiamata`() {
        val motore = motoreSherpaSenzaNativi()
        val embedding = modello.su(motore)

        val risultati = List(N_CHIAMATE) { i -> embedding.calcola(FloatArray(i + 1) { 1f }) }

        assertEquals(1, modello.caricamenti)
        assertEquals(N_CHIAMATE, modello.calcoli)
        assertEquals(N_CHIAMATE, motore.sessioni)
        assertContentEquals(floatArrayOf(3f, 3f), risultati[2])
    }

    @Test
    fun `AC-491 il Mutex e libero tra due calcola`() {
        val motore = motoreSherpaSenzaNativi()
        val embedding = modello.su(motore)
        val altro = Executors.newSingleThreadExecutor()
        try {
            embedding.calcola(FloatArray(4))

            val sonda = altro.submit<String> { motore.conSessione(ConfigSessione(emptyList(), 1)) { "libero" } }

            assertEquals("libero", sonda.get(ATTESA_S, TimeUnit.SECONDS))
            embedding.calcola(FloatArray(4))
            assertEquals(1, modello.caricamenti, "il modello resta in cache")
        } finally {
            altro.shutdownNow()
        }
    }

    @Test
    fun `AC-491 close rilascia il modello e il calcola successivo lo ricarica`() {
        val motore = motoreSherpaSenzaNativi()
        val embedding = modello.su(motore)

        embedding.calcola(FloatArray(4))
        embedding.close()
        embedding.close()
        embedding.calcola(FloatArray(4))

        assertEquals(1, modello.rilasci)
        assertEquals(2, modello.caricamenti)
    }

    @Test
    fun `AC-491 close senza caricamenti non apre sessioni e non carica nulla`() {
        val motore = motoreSherpaSenzaNativi()

        modello.su(motore).close()

        assertEquals(0, motore.sessioni)
        assertEquals(0, modello.caricamenti)
    }

    @Test
    fun `AC-491 un calcola che fallisce rilascia il Mutex`() {
        val motore = motoreSherpaSenzaNativi()
        val guasto = ModelloEmbeddingFinto { error("guasto nativo") }.su(motore)

        assertFailsWith<IllegalStateException> { guasto.calcola(FloatArray(4)) }

        assertEquals("libero", motore.conSessione(ConfigSessione(emptyList(), 1)) { "libero" })
    }

    private companion object {
        const val N_CHIAMATE = 5
        const val ATTESA_S = 5L
    }
}
