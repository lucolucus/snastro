package snastro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state.
 * [onRegistrazioniSelezionata]/[onModelliELicenze] (rework cycle 1, HIGH #9): optional hooks the
 * composition root wires to its own S5/sub-section navigation — the shell state has no such section.
 */
@Composable
fun ShellRoute(
    presenter: ShellPresenter,
    contenutoSenzaProgetto: @Composable () -> Unit = {},
    contenuto: @Composable (ShellUiStato.ConProgetto) -> Unit = {},
    onRegistrazioniSelezionata: (() -> Unit)? = null,
    onModelliELicenze: (() -> Unit)? = null,
) {
    val stato by presenter.stato.collectAsState()
    SchermataShell(
        stato = stato,
        azioni = presenter.azioni,
        contenutoSenzaProgetto = contenutoSenzaProgetto,
        contenuto = contenuto,
        onRegistrazioniSelezionata = onRegistrazioniSelezionata,
        onModelliELicenze = onModelliELicenze,
    )
}
