package snastro.kernel

class UnitaDiLavoroFintaTest : UnitaDiLavoroContratto() {
    override fun ambiente(): Ambiente {
        val effetti = EffettiInMemoria()
        return object : Ambiente {
            override val unitaDiLavoro = UnitaDiLavoroFinta(effetti)

            override fun scrivi(effetto: String) = effetti.scrivi(effetto)

            override fun effetti() = effetti.visibili()
        }
    }
}
