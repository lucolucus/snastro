package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.parlanti.dominio.Impronta
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Consumer-driven contract of [ConfrontoImpronte] (boundary `tec-confronto-impronte`, ADR 0004): an
 * identical print is [Fascia.FORTE], an empty list is [Fascia.NESSUNA], the result is the BEST band over
 * the list, inputs are never modified; [SoglieFascia] refuses forte <= debole. One subclass per implementation.
 */
public abstract class ConfrontoImpronteContratto {
    /** The comparison under test. */
    protected abstract fun confronto(): ConfrontoImpronte

    @Test
    public fun `AC-41 un impronta identica e FORTE`() {
        assertEquals(Fascia.FORTE, confronto().fascia(voce(), listOf(voce())))
    }

    @Test
    public fun `AC-41 una lista vuota e NESSUNA`() {
        assertEquals(Fascia.NESSUNA, confronto().fascia(voce(), emptyList()))
    }

    @Test
    public fun `AC-41 conta la fascia migliore tra le impronte in qualunque ordine`() {
        val c = confronto()

        assertEquals(Fascia.FORTE, c.fascia(voce(), listOf(opposta(), voce())))
        assertEquals(Fascia.FORTE, c.fascia(voce(), listOf(voce(), opposta())))
    }

    @Test
    public fun `AC-41 la fascia di una lista e la migliore delle fasce delle singole impronte`() {
        val c = confronto()
        val impronte = listOf(opposta(), vicina(), diagonale(), voce())

        for (fine in 1..impronte.size) {
            val lista = impronte.take(fine)
            val migliore = lista.map { c.fascia(voce(), listOf(it)) }.min()
            assertEquals(migliore, c.fascia(voce(), lista), "lista di $fine impronte")
        }
    }

    @Test
    public fun `AC-41 confrontare non modifica le impronte ricevute`() {
        val v = voce()
        val altre = listOf(opposta(), vicina())

        confronto().fascia(v, altre)

        assertContentEquals(voce().valori, v.valori)
        assertEquals(listOf(opposta(), vicina()), altre)
    }

    @Test
    public fun `AC-41 SoglieFascia con forte minore o uguale a debole sono rifiutate`() {
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = 0.5, debole = 0.7) }
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = 0.6, debole = 0.6) }
        assertFailsWith<IllegalArgumentException> { SoglieFascia(forte = Double.NaN, debole = 0.5) }
        assertEquals(0.7, SoglieFascia(forte = 0.7, debole = 0.5).forte)
    }

    private fun voce() = Impronta(floatArrayOf(1f, 0f, 0f, 0f))

    private fun opposta() = Impronta(floatArrayOf(-1f, 0f, 0f, 0f))

    private fun vicina() = Impronta(floatArrayOf(1f, 0.3f, 0f, 0f))

    private fun diagonale() = Impronta(floatArrayOf(1f, 1f, 0f, 0f))
}
