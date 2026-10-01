package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.unIncontroDi
import snastro.trascrizione.dominio.unaRadice
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** D1 (dev-architecture-app.md#porta-contratto): the contract passes on the fake, multi-Parte cases included. */
class VociDellIncontroRepositoryFintaTest : VociDellIncontroRepositoryContratto() {
    override val piuPartiPerIncontro: Boolean = true

    override fun repository(): VociDellIncontroRepository = VociDellIncontroRepositoryFinta()

    @Test
    fun `una Parte gia' in un'altra radice e' rifiutata come dalla chiave del Trascritto`() {
        val repo = VociDellIncontroRepositoryFinta()
        repo.salva(unaRadice(registrazioneId = REGISTRAZIONE))

        assertFailsWith<IllegalStateException> {
            repo.salva(unaRadice(registrazioneId = REGISTRAZIONE, incontroId = unIncontroDi(ALTRA_REGISTRAZIONE)))
        }
    }

    @Test
    fun `un rollback di UnitaDiLavoroFinta annulla il salva`() {
        val repo = VociDellIncontroRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)

        uow.inTransazione<Unit> {
            repo.salva(unaRadice(registrazioneId = REGISTRAZIONE))
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }

        assertNull(repo.trova(unIncontroDi(REGISTRAZIONE)))
    }
}
