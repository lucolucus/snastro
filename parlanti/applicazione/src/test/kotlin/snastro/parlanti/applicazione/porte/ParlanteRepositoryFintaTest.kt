package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParlanteRepositoryFintaTest : ParlanteRepositoryContratto() {
    override fun repository(): ParlanteRepository = ParlanteRepositoryFinta()

    @Test
    fun `AC-37 la Finta segue il rollback di UnitaDiLavoroFinta`() {
        val repo = ParlanteRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)
        uow.inTransazione { repo.salva(unParlante("id-1", "Marco")) }.atteso()

        uow.inTransazione {
            repo.rimuovi(ParlanteId("id-1"))
            repo.salva(unParlante("id-2", "Anna")).atteso()
            repo.salva(unParlante("id-3", "anna"))
        }.erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("Marco", repo.trova(ParlanteId("id-1"))?.nome?.valore)
        assertNull(repo.trova(ParlanteId("id-2")))
    }

    @Test
    fun `AC-37 un Errore restituito dopo salva annulla il salvataggio`() {
        val repo = ParlanteRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)

        uow.inTransazione<Unit> {
            repo.salva(unParlante("id-1", "Marco")).atteso()
            Esito.Errore(ErroreParlanti.NomeVuoto)
        }

        assertEquals(emptyList(), repo.delProgetto(PROGETTO))
    }

    private fun unParlante(id: String, nome: String): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(nome).atteso(), TipoParlante.RICORRENTE).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
    }
}
