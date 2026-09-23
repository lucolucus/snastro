package snastro.parlanti.applicazione.porte

import snastro.parlanti.dominio.Impronta
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfrontoImpronteFintaTest : ConfrontoImpronteContratto() {
    override fun confronto(): ConfrontoImpronte = ConfrontoImpronteFinta()

    @Test
    fun `AC-41 la Finta restituisce la fascia programmata per l impronta del Parlante e la migliore tra piu`() {
        val debole = Impronta(floatArrayOf(0f, 1f))
        val nessuna = Impronta(floatArrayOf(0f, 2f))
        val finta = ConfrontoImpronteFinta(mapOf(debole to Fascia.DEBOLE))
        val voce = Impronta(floatArrayOf(1f, 0f))

        assertEquals(Fascia.DEBOLE, finta.fascia(voce, listOf(debole)))
        assertEquals(Fascia.NESSUNA, finta.fascia(voce, listOf(nessuna)))
        assertEquals(Fascia.DEBOLE, finta.fascia(voce, listOf(nessuna, debole)))
        assertEquals(Fascia.FORTE, finta.fascia(voce, listOf(debole, voce)))
    }
}
