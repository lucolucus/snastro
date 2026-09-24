package snastro.avvio.r2

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import snastro.ui.registrazione.ComandiVoce
import snastro.ui.registrazione.ComandiVoceContratto
import snastro.ui.registrazione.ComandoVoce
import java.time.Clock

/** D2 (AC-418): the per-project adapter honours the consumer's [ComandiVoce] contract unchanged. */
class ComandiVoceProgettoTest : ComandiVoceContratto() {
    override fun con(
        progetto: CoroutineScope,
        clock: Clock,
        esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    ): ComandiVoce = ComandiVoceProgetto(progetto, clock, esecutore)
}
