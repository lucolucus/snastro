package snastro.trascrizione.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

class NumeroPersoneTest {
    @Test
    fun `AC-368 NumeroPersone di accetta 1 e 10`() {
        listOf(1, 10).forEach { n -> assertEquals(n, NumeroPersone.di(n).atteso().valore, "n = $n") }
    }

    @Test
    fun `AC-368 NumeroPersone di rifiuta 0 -1 e 11 con NumeroPersoneFuoriIntervallo`() {
        listOf(0, -1, 11).forEach { n ->
            val errore = NumeroPersone.di(n).erroreAtteso<ErroreTrascrizione.NumeroPersoneFuoriIntervallo>()
            assertEquals(ErroreTrascrizione.NumeroPersoneFuoriIntervallo(n), errore, "n = $n")
        }
    }
}
