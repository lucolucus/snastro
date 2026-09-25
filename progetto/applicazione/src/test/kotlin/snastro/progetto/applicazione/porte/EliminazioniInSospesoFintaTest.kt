package snastro.progetto.applicazione.porte

import snastro.kernel.UnitaDiLavoroFinta

class EliminazioniInSospesoFintaTest : EliminazioniInSospesoContratto() {
    override fun ambiente(): Ambiente {
        val inSospeso = EliminazioniInSospesoFinta()
        return object : Ambiente {
            override val inSospeso = inSospeso
            override val unitaDiLavoro = UnitaDiLavoroFinta(inSospeso)
        }
    }
}
