package snastro.parlanti.dominio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ImprontaTest {
    @Test
    fun `due impronte con gli stessi valori sono uguali per contenuto`() {
        assertEquals(Impronta(floatArrayOf(1f, 2f)), Impronta(floatArrayOf(1f, 2f)))
        assertEquals(Impronta(floatArrayOf(1f, 2f)).hashCode(), Impronta(floatArrayOf(1f, 2f)).hashCode())
        assertNotEquals(Impronta(floatArrayOf(1f, 2f)), Impronta(floatArrayOf(2f, 1f)))
    }

    @Test
    fun `mutare l'array sorgente o una copia ottenuta non cambia l'Impronta`() {
        val sorgente = floatArrayOf(1f, 2f)
        val impronta = Impronta(sorgente)
        val hashIniziale = impronta.hashCode()

        sorgente[0] = 9f
        impronta.valori[1] = 9f

        assertContentEquals(floatArrayOf(1f, 2f), impronta.valori)
        assertEquals(Impronta(floatArrayOf(1f, 2f)), impronta)
        assertEquals(hashIniziale, impronta.hashCode())
    }

    @Test
    fun `dimensione restituisce il numero di valori`() {
        assertEquals(3, Impronta(floatArrayOf(1f, 2f, 3f)).dimensione)
        assertEquals(0, Impronta(FloatArray(0)).dimensione)
    }

    @Test
    fun `get legge il valore alla posizione e fuori intervallo fallisce`() {
        val impronta = Impronta(floatArrayOf(0.5f, -1.5f))

        assertEquals(listOf(0.5f, -1.5f), (0 until impronta.dimensione).map { impronta[it] })
        assertFailsWith<IndexOutOfBoundsException> { impronta[2] }
    }

    @Test
    fun `toString non espone i valori biometrici`() {
        val testo = Impronta(floatArrayOf(0.123f, 4.5f)).toString()

        assertFalse(testo.contains("0.123") || testo.contains("4.5"), testo)
        assertEquals("Impronta(2 valori)", testo)
    }
}
