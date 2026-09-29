package snastro.ui.progetti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state.
 * [cartellaGenitorePredefinita] (ADR 0010, or the folder saved in Impostazioni) is `:avvio`'s own injected default,
 * forwarded unchanged; [sceltaCartella] (L464d) is `:avvio`'s window-owned folder picker, forwarded unchanged too;
 * [onImpostazioni] opens the Impostazioni screen.
 */
@Composable
fun ProgettiRoute(
    presenter: ProgettiPresenter,
    cartellaGenitorePredefinita: String,
    sceltaCartella: SceltaCartella,
    onImpostazioni: (() -> Unit)? = null,
) {
    val stato by presenter.stato.collectAsState()
    SchermataProgetti(stato, presenter.azioni, cartellaGenitorePredefinita, sceltaCartella, onImpostazioni)
}
