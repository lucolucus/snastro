package snastro.ui.registrazione

import java.time.Instant

/** One entry of [ComandiVoce.stato]: a card command still running since [avviatoAlle] (AC-412/AC-415). */
data class StatoComando(val avviatoAlle: Instant)
