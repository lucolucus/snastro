package snastro.ui.progetti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state.
 * [cartellaGenitorePredefinita] (ADR 0010) is `:avvio`'s own injected default, forwarded unchanged.
 */
@Composable
fun ProgettiRoute(presenter: ProgettiPresenter, cartellaGenitorePredefinita: String) {
    val stato by presenter.stato.collectAsState()
    SchermataProgetti(stato, presenter.azioni, cartellaGenitorePredefinita)
}
