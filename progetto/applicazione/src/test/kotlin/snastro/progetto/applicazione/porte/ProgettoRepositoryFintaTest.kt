package snastro.progetto.applicazione.porte

import snastro.kernel.UnitaDiLavoroFinta

class ProgettoRepositoryFintaTest : ProgettoRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val progetti = ProgettoRepositoryFinta()
        return object : Ambiente {
            override val progetti = progetti
            override val unitaDiLavoro = UnitaDiLavoroFinta(progetti)
        }
    }
}
