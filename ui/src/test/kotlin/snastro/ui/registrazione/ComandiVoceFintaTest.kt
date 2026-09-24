package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import java.time.Clock

/** D1: the fake honours the [ComandiVoce] contract. */
class ComandiVoceFintaTest : ComandiVoceContratto() {
    override fun con(
        progetto: CoroutineScope,
        clock: Clock,
        esecutoreFrase: suspend (FraseRef, PassiNominaFrase) -> Esito<Unit>,
        esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    ): ComandiVoce = ComandiVoceFinta(progetto, clock, rispostaFrase = esecutoreFrase, risposta = esecutore)
}
