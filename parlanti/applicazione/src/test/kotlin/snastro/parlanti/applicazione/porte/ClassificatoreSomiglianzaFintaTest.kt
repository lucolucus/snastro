package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.parlanti.dominio.Impronta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [ClassificatoreSomiglianzaFinta] against the consumer-driven contract (AC-495: D1, green on its
 * own) plus its own scripting/recording behaviour.
 */
class ClassificatoreSomiglianzaFintaTest : ClassificatoreSomiglianzaContratto() {
    override fun classificatore(): ClassificatoreSomiglianza = ClassificatoreSomiglianzaFinta()

    @Test
    fun `AC-495 restituisce la Classificazione programmata per indice e Incerta per gli indici non programmati`() {
        val finta = ClassificatoreSomiglianzaFinta(
            programmate = mapOf(0 to Classificazione.Sicura(A), 2 to Classificazione.Sicura(B)),
        )
        val frasi = listOf(impronta(), impronta(), impronta())

        assertEquals(
            listOf(Classificazione.Sicura(A), Classificazione.Incerta, Classificazione.Sicura(B)),
            finta.classifica(mapOf(A to listOf(impronta()), B to listOf(impronta())), frasi),
        )
    }

    @Test
    fun `AC-495 registra il riferimenti map ricevuto ad ogni chiamata`() {
        val finta = ClassificatoreSomiglianzaFinta()
        val primo = mapOf(A to listOf(impronta()), B to listOf(impronta()))
        val secondo = mapOf(A to listOf(impronta(), impronta()), B to listOf(impronta()))

        finta.classifica(primo, listOf(impronta()))
        finta.classifica(secondo, listOf(impronta()))

        assertEquals(listOf(primo, secondo), finta.chiamate)
    }

    @Test
    fun `AC-495 rifiuta meno di due Parlanti di riferimento o un Parlante senza impronte`() {
        val finta = ClassificatoreSomiglianzaFinta()

        assertFailsWith<IllegalArgumentException> { finta.classifica(mapOf(A to listOf(impronta())), listOf(impronta())) }
        assertFailsWith<IllegalArgumentException> {
            finta.classifica(mapOf(A to listOf(impronta()), B to emptyList()), listOf(impronta()))
        }
    }

    private fun impronta() = Impronta(floatArrayOf(1f, 0f))

    private companion object {
        val A = ParlanteId("parlante-a")
        val B = ParlanteId("parlante-b")
    }
}
