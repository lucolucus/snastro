package snastro.ui.coda

/**
 * Fake [PosizioniNellaCoda] (RC-9) for presenter tests (AC-S25): [istantanea] returns the snapshot
 * last assigned to [posizioni] and counts its calls in [letture], so a presenter test can assert it
 * re-reads the positions on every `Cambiamento`.
 */
class PosizioniNellaCodaFinta(iniziali: PosizioniCoda = PosizioniCoda.VUOTA) : PosizioniNellaCoda {
    @Volatile var posizioni: PosizioniCoda = iniziali

    @Volatile var letture: Int = 0
        private set

    @Synchronized
    override fun istantanea(): PosizioniCoda {
        letture++
        return posizioni
    }
}
