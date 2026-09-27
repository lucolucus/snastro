package snastro.kernel

/** AC-C15: [UnitaDiLavoroFinta] passes [LetturaCoerenteContratto]; its fake write refuses while a read is open. */
class LetturaCoerenteFintaTest : LetturaCoerenteContratto() {
    override fun ambiente(): Ambiente {
        val effetti = EffettiInMemoria()
        val uow = UnitaDiLavoroFinta(effetti)
        return object : Ambiente {
            override val lettura = uow

            override val unitaDiLavoro = uow

            override fun scrivi(effetto: String) {
                check(!uow.letturaAperta) { "scrittura dentro inLettura" }
                effetti.scrivi(effetto)
            }

            override fun effetti() = effetti.visibili().toList()

            override fun leggi() = uow.inLettura { effetti.visibili() }
        }
    }
}
