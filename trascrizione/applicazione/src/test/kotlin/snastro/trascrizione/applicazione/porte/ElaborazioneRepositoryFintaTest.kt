package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.assertEquals

class ElaborazioneRepositoryFintaTest : ElaborazioneRepositoryContratto() {
    override fun repository(): ElaborazioneRepository = ElaborazioneRepositoryFinta()

    @Test
    fun `AC-29 la Finta segue il rollback di UnitaDiLavoroFinta anche per le transizioni`() {
        val repo = ElaborazioneRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)
        val id = RegistrazioneId("registrazione-1")
        uow.inTransazione { repo.salva(unaElaborazione(registrazioneId = id)) }.atteso()

        uow.inTransazione<Unit> {
            val e = repo.inAttesa().single()
            e.avvia(AVVIATA).atteso()
            repo.salva(e).atteso()
            repo.salva(unaElaborazione(id = ElaborazioneId("id-9"), registrazioneId = ALTRA)).atteso()
            Esito.Errore(ErroreDiProva.Fallito("politica violata"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(listOf(StatoElaborazione.IN_ATTESA), repo.diRegistrazione(id).map { it.stato })
        assertEquals(emptyList(), repo.diRegistrazione(ALTRA))
    }

    private companion object {
        val ALTRA = RegistrazioneId("registrazione-altra")
        val AVVIATA: Instant = Instant.parse("2026-09-23T10:05:00Z")
    }
}
