package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import java.time.Clock

/** D1: the fake honours the [ComandiVoce] contract. */
class ComandiVoceFintaTest : ComandiVoceContratto() {
    override fun con(
        progetto: CoroutineScope,
        clock: Clock,
        esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    ): ComandiVoce = ComandiVoceFinta(progetto, clock, risposta = esecutore)
}
