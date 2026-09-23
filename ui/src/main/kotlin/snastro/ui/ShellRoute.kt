package snastro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state. */
@Composable
fun ShellRoute(
    presenter: ShellPresenter,
    contenutoSenzaProgetto: @Composable () -> Unit = {},
    contenuto: @Composable (ShellUiStato.ConProgetto) -> Unit = {},
) {
    val stato by presenter.stato.collectAsState()
    SchermataShell(stato, presenter.azioni, contenutoSenzaProgetto, contenuto)
}
