package snastro.ui.registrazioni

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state. */
@Composable
fun RegistrazioniRoute(presenter: RegistrazioniPresenter) {
    val stato by presenter.stato.collectAsState()
    SchermataRegistrazioni(stato, presenter.azioni)
}
