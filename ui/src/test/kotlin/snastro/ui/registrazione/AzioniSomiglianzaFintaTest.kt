package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import java.time.Clock

/** D1: the fake honours the [AzioniSomiglianza] contract. */
class AzioniSomiglianzaFintaTest : AzioniSomiglianzaContratto() {
    override fun con(progetto: CoroutineScope, clock: Clock, scenario: ScenarioSomiglianza): SondaSomiglianza {
        val finta = AzioniSomiglianzaFinta(clock, scenario.gruppi, scenario.incerte)
        return SondaSomiglianza(finta) { finta.applicazioni.size }
    }
}
