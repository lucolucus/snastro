package snastro.ui.modelli

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state. */
@Composable
fun ModelliRoute(presenter: ModelliPresenter) {
    val stato by presenter.stato.collectAsState()
    SchermataModelli(stato, presenter.azioni)
}
