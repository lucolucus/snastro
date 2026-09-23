package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta

class RegistrazioneRepositoryFintaTest : RegistrazioneRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val registrazioni = RegistrazioneRepositoryFinta()
        return object : Ambiente {
            override val registrazioni = registrazioni
            override val unitaDiLavoro = UnitaDiLavoroFinta(registrazioni)
            override val progettoId = ProgettoId("id-1")
        }
    }
}
