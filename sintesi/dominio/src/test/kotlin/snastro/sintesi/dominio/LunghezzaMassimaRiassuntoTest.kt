package snastro.sintesi.dominio

import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

class LunghezzaMassimaRiassuntoTest {
    private val progettoId = ProgettoId("id-1")

    @Test
    fun `AC-S48 predefinita vale 2000 parole per un Progetto senza impostazione`() {
        val lunghezza = LunghezzaMassimaRiassunto.predefinita(progettoId)

        assertEquals(progettoId, lunghezza.progettoId)
        assertEquals(2000, lunghezza.parole.valore)
    }

    @Test
    fun `AC-S49 modifica a 1500 cambia il valore e restituisce LunghezzaMassimaRiassuntoModificata`() {
        val lunghezza = LunghezzaMassimaRiassunto.predefinita(progettoId)

        val evento = lunghezza.modifica(1500).atteso()

        assertEquals(LunghezzaMassimaRiassuntoModificataDominio(progettoId), evento)
        assertEquals(1500, lunghezza.parole.valore)
    }

    @Test
    fun `AC-S49 modifica a 2600 restituisce LunghezzaMassimaFuoriIntervallo e lascia il valore invariato`() {
        val lunghezza = LunghezzaMassimaRiassunto.predefinita(progettoId)
        lunghezza.modifica(1500).atteso()

        val errore = lunghezza.modifica(2600).erroreAtteso<ErroreSintesi.LunghezzaMassimaFuoriIntervallo>()

        assertEquals(ErroreSintesi.LunghezzaMassimaFuoriIntervallo(2600, 300, 2500), errore)
        assertEquals(1500, lunghezza.parole.valore)
    }
}
