package snastro.ui.lettore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state.
 * [onRiproduci] is supplied by the embedding screen — it knows which `RegistrazioneId`/`EstrattoRef`
 * this instance of the bar plays; `pausa` never needs a target, so it is always [presenter]'s own.
 */
@Composable
fun LettoreRoute(presenter: LettorePresenter, onRiproduci: () -> Unit, modifier: Modifier = Modifier) {
    val stato by presenter.stato.collectAsState()
    BarraLettore(stato, onRiproduci, presenter::pausa, modifier)
}
