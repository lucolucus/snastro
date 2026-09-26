package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto
import kotlin.test.assertEquals

/** AC-S70 (D1): [LunghezzaMassimaRiassuntoRepositoryFinta] passes the contract; plus its rollback. */
class LunghezzaMassimaRiassuntoRepositoryFintaTest : LunghezzaMassimaRiassuntoRepositoryContratto() {
    override fun repository(): LunghezzaMassimaRiassuntoRepository = LunghezzaMassimaRiassuntoRepositoryFinta()

    @Test
    fun `la Finta torna allo stato di prima su un rollback di UnitaDiLavoroFinta`() {
        val repo = LunghezzaMassimaRiassuntoRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)

        uow.inTransazione {
            repo.salva(LunghezzaMassimaRiassunto.predefinita(PROGETTO).also { it.modifica(900).atteso() }).atteso()
            Esito.Errore(snastro.sintesi.dominio.ErroreSintesi.ModelloNonInstallato)
        }

        assertEquals(2000, repo.trova(PROGETTO).parole.valore)
    }
}
