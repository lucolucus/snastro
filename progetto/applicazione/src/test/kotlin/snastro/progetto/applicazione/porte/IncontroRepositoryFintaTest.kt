package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta

class IncontroRepositoryFintaTest : IncontroRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val registrazioni = RegistrazioneRepositoryFinta()
        val incontri = IncontroRepositoryFinta(registrazioni)
        return object : Ambiente {
            override val incontri = incontri
            override val registrazioni = registrazioni
            override val unitaDiLavoro = UnitaDiLavoroFinta(registrazioni, incontri)
            override val progettoId = ProgettoId("id-1")
            override val incontriConPiuParti = true
        }
    }
}
