package snastro.sintesi.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

class LunghezzaMassimaParoleTest {
    @Test
    fun `INV-S9 di accetta le parole in 300-10000 e rifiuta il resto con LunghezzaMassimaFuoriIntervallo`() {
        listOf(300, 2000, 10000).forEach { n -> assertEquals(n, LunghezzaMassimaParole.di(n).atteso().valore) }
        listOf(299, 10001, 0, -1).forEach { n ->
            assertEquals(
                ErroreSintesi.LunghezzaMassimaFuoriIntervallo(n, 300, 10000),
                LunghezzaMassimaParole.di(n).erroreAtteso<ErroreSintesi.LunghezzaMassimaFuoriIntervallo>(),
            )
        }
    }

    @Test
    fun `INV-S9 le costanti valgono 300 10000 e 2000`() {
        assertEquals(300, LunghezzaMassimaParole.MINIMO)
        assertEquals(10000, LunghezzaMassimaParole.MASSIMO)
        assertEquals(2000, LunghezzaMassimaParole.PREDEFINITA)
    }
}
