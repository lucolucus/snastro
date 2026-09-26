package snastro.sintesi.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryFinta
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-S109: the `vista-impostazioni-sintesi` read-model, over [LunghezzaMassimaRiassuntoRepositoryFinta] (D1,
 * consumer-driven — the real `LunghezzaMassimaRiassuntoRepositorySql` runs the same repository contract).
 */
class ImpostazioniSintesiLetturaTest {
    private val progetto = ProgettoId("progetto-1")
    private val altroProgetto = ProgettoId("progetto-2")

    @Test
    fun `AC-S109 senza riga la vista mostra la predefinita 2000 e i limiti 300 2500`() {
        val lettura = ImpostazioniSintesiLettura(LunghezzaMassimaRiassuntoRepositoryFinta())

        val vista = lettura.di(progetto)

        assertEquals(ImpostazioniSintesiVista(lunghezzaMassimaParole = 2000, minimo = 300, massimo = 2500), vista)
    }

    @Test
    fun `AC-S109 dopo Modifica a 1500 la vista mostra 1500 e un altro Progetto resta a 2000`() {
        val repository = LunghezzaMassimaRiassuntoRepositoryFinta()
        val lettura = ImpostazioniSintesiLettura(repository)
        val modificata = LunghezzaMassimaRiassunto.predefinita(progetto).also { it.modifica(1500).atteso() }
        repository.salva(modificata).atteso()

        val vista = lettura.di(progetto)
        val vistaAltroProgetto = lettura.di(altroProgetto)

        assertEquals(
            ImpostazioniSintesiVista(lunghezzaMassimaParole = 1500, minimo = 300, massimo = 2500),
            vista,
        )
        assertEquals(
            ImpostazioniSintesiVista(lunghezzaMassimaParole = 2000, minimo = 300, massimo = 2500),
            vistaAltroProgetto,
        )
    }
}
