package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import kotlin.test.assertEquals

/** AC-S65..S69 (D1): [RiassuntoRepositoryFinta] passes [RiassuntoRepositoryContratto]; plus its rollback. */
class RiassuntoRepositoryFintaTest : RiassuntoRepositoryContratto() {
    override fun repository(): RiassuntoRepository = RiassuntoRepositoryFinta()

    @Test
    fun `la Finta torna allo stato di prima su un rollback di UnitaDiLavoroFinta`() {
        val repo = RiassuntoRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)
        uow.inTransazione { repo.salva(unRiassunto("riassunto-1", REGISTRAZIONE)) }.atteso()

        uow.inTransazione {
            repo.rimuoviDiRegistrazione(REGISTRAZIONE).atteso()
            repo.salva(unRiassunto("riassunto-2", REGISTRAZIONE))
            repo.salva(unRiassunto("riassunto-3", REGISTRAZIONE))
        }

        assertEquals(listOf("riassunto-1"), repo.inAttesa().map { it.id.valore })
        assertEquals(Esito.Ok(1), repo.rimuoviDiRegistrazione(REGISTRAZIONE))
    }
}
