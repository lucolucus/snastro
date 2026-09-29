package snastro.ui.impostazioni

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import snastro.ui.progetti.SceltaCartella

/**
 * One-line composition entry point (dev-architecture `#presenter`): collects [presenter]'s state and, when a
 * project is open, [lunghezzaRiassunto]'s (named [nomeProgetto]). [modelli] is S5 as the composition root renders
 * it; [onIndietro] is wired only when the screen is full-window (reached from S1).
 */
@Suppress("LongParameterList") // presenter + the port + the S5 slot + the open project's part + the back hook
@Composable
fun ImpostazioniRoute(
    presenter: ImpostazioniPresenter,
    sceltaCartella: SceltaCartella,
    modelli: @Composable () -> Unit,
    nomeProgetto: String? = null,
    lunghezzaRiassunto: LunghezzaRiassuntoPresenter? = null,
    onIndietro: (() -> Unit)? = null,
) {
    val stato by presenter.stato.collectAsState()
    val riassunto = if (nomeProgetto != null && lunghezzaRiassunto != null) {
        val statoRiassunto by lunghezzaRiassunto.stato.collectAsState()
        RiassuntoImpostazioni(nomeProgetto, statoRiassunto, lunghezzaRiassunto.azioni)
    } else {
        null
    }
    SchermataImpostazioni(
        stato = stato,
        azioni = presenter.azioni,
        riassunto = riassunto,
        sceltaCartella = sceltaCartella,
        modelli = modelli,
        onIndietro = onIndietro,
    )
}
