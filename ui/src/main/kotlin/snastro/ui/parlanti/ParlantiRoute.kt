package snastro.ui.parlanti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state. */
@Composable
fun ParlantiRoute(presenter: ParlantiPresenter) {
    val stato by presenter.stato.collectAsState()
    SchermataParlanti(stato, presenter.azioni)
}
