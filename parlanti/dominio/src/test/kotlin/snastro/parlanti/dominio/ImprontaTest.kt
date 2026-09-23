package snastro.parlanti.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `toString non espone i valori biometrici`() {
        val testo = Impronta(floatArrayOf(0.123f, 4.5f)).toString()

        assertFalse(testo.contains("0.123") || testo.contains("4.5"), testo)
    }
}
