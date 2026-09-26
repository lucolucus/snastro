package snastro.sintesi.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

class LunghezzaMassimaParoleTest {
    @Test
    fun `INV-S9 di accetta le parole in 300-2500 e rifiuta il resto con LunghezzaMassimaFuoriIntervallo`() {
        listOf(300, 2000, 2500).forEach { n -> assertEquals(n, LunghezzaMassimaParole.di(n).atteso().valore) }
        listOf(299, 2501, 0, -1).forEach { n ->
            assertEquals(
                ErroreSintesi.LunghezzaMassimaFuoriIntervallo(n, 300, 2500),
                LunghezzaMassimaParole.di(n).erroreAtteso<ErroreSintesi.LunghezzaMassimaFuoriIntervallo>(),
            )
        }
    }

    @Test
    fun `INV-S9 le costanti valgono 300 2500 e 2000`() {
        assertEquals(300, LunghezzaMassimaParole.MINIMO)
        assertEquals(2500, LunghezzaMassimaParole.MASSIMO)
        assertEquals(2000, LunghezzaMassimaParole.PREDEFINITA)
    }
}
