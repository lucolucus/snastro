package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.parlanti.dominio.Impronta
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [ConfrontoImpronte] (boundary `tec-confronto-impronte`, ADR 0004): an
 * identical print is [Fascia.FORTE], an empty list is [Fascia.NESSUNA], the result is the BEST band over
 * the list, inputs are never modified, non-comparable prints and dimension mismatches are NESSUNA without
 * throwing (see [ConfrontoImpronte]). One subclass per implementation.
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
    public fun `AC-41 un impronta nulla o non finita nella lista non maschera una FORTE`() {
        val c = confronto()
        val nonConfrontabili = listOf(nulla(), nonFinita(), Impronta(floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f, 0f)))

        assertEquals(Fascia.FORTE, c.fascia(voce(), nonConfrontabili + voce()))
        assertEquals(Fascia.FORTE, c.fascia(voce(), listOf(voce()) + nonConfrontabili))
        assertEquals(Fascia.NESSUNA, c.fascia(voce(), nonConfrontabili))
    }

    @Test
    public fun `AC-41 una voce nulla o non finita e NESSUNA senza eccezioni`() {
        val c = confronto()

        assertEquals(Fascia.NESSUNA, c.fascia(nulla(), listOf(voce(), nulla())))
        assertEquals(Fascia.NESSUNA, c.fascia(nonFinita(), listOf(voce(), nonFinita())))
        assertEquals(Fascia.NESSUNA, c.fascia(Impronta(FloatArray(0)), listOf(Impronta(FloatArray(0)))))
    }

    @Test
    public fun `AC-41 un impronta di dimensione diversa e NESSUNA e non maschera una FORTE`() {
        val c = confronto()
        val corta = Impronta(floatArrayOf(1f, 0f, 0f))
        val lunga = Impronta(floatArrayOf(1f, 0f, 0f, 0f, 0f))

        assertEquals(Fascia.NESSUNA, c.fascia(voce(), listOf(corta, lunga)))
        assertEquals(Fascia.FORTE, c.fascia(voce(), listOf(corta, voce(), lunga)))
    }

    private fun voce() = Impronta(floatArrayOf(1f, 0f, 0f, 0f))

    private fun opposta() = Impronta(floatArrayOf(-1f, 0f, 0f, 0f))

    private fun vicina() = Impronta(floatArrayOf(1f, 0.3f, 0f, 0f))

    private fun diagonale() = Impronta(floatArrayOf(1f, 1f, 0f, 0f))

    private fun nulla() = Impronta(floatArrayOf(0f, 0f, 0f, 0f))

    private fun nonFinita() = Impronta(floatArrayOf(1f, Float.NaN, 0f, 0f))
}
