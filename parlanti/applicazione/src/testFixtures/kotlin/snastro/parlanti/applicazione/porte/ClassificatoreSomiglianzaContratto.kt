package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.parlanti.dominio.Impronta
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [ClassificatoreSomiglianza] (boundary `tec-classificatore-somiglianza`,
 * ADR 0019 §4.3): the structural properties EVERY implementation must uphold — one [Classificazione]
 * per frase, same size and order as the input, and never an exception on print data (a scripted fake
 * cannot honour real cosine math, so the concrete banding is each implementation's OWN test, e.g.
 * `ClassificatoreSomiglianzaCosenoTest` AC-497..500). One subclass per implementation.
 */
public abstract class ClassificatoreSomiglianzaContratto {
    /** The classifier under test. */
    protected abstract fun classificatore(): ClassificatoreSomiglianza

    @Test
    public fun `AC-495 l'output ha una Classificazione per ogni frase nello stesso ordine`() {
        val c = classificatore()
        val riferimenti = mapOf(A to listOf(unitaria(1f, 0f)), B to listOf(unitaria(0f, 1f)))
        val frasi = listOf(unitaria(1f, 0f), unitaria(0f, 1f), unitaria(0.5f, 0.5f), unitaria(-1f, 0f))

        assertEquals(frasi.size, c.classifica(riferimenti, frasi).size)
    }

    @Test
    public fun `AC-495 nessuna frase produce un risultato vuoto`() {
        val c = classificatore()
        val riferimenti = mapOf(A to listOf(unitaria(1f, 0f)), B to listOf(unitaria(0f, 1f)))

        assertEquals(emptyList(), c.classifica(riferimenti, emptyList()))
    }

    @Test
    public fun `AC-495 una frase non confrontabile non lancia mai un eccezione`() {
        val c = classificatore()
        val riferimenti = mapOf(A to listOf(unitaria(1f, 0f)), B to listOf(unitaria(0f, 1f)))
        val nonConfrontabili = listOf(
            Impronta(floatArrayOf(0f, 0f)),
            Impronta(floatArrayOf(Float.NaN, 0f)),
            Impronta(floatArrayOf(Float.POSITIVE_INFINITY, 0f)),
            Impronta(FloatArray(0)),
            Impronta(floatArrayOf(1f, 0f, 0f)), // dimension mismatch with the 2D references
        )

        assertEquals(nonConfrontabili.size, c.classifica(riferimenti, nonConfrontabili).size)
    }

    private fun unitaria(x: Float, y: Float) = Impronta(floatArrayOf(x, y))

    private companion object {
        val A = ParlanteId("parlante-a")
        val B = ParlanteId("parlante-b")
    }
}
