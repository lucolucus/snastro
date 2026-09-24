package snastro.avvio.r1

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import snastro.kernel.RegistrazioneId
import snastro.ui.AzioniShell
import snastro.ui.DestinazioneShell
import snastro.ui.ShellPresenter
import snastro.ui.ShellRoute
import snastro.ui.ShellUiStato

/**
 * The open project's place inside the Registrazioni section (S2 list, S3 of one Registrazione, S5), as
 * Compose observable state — the composition root's own navigation, shared by R1 and R2 (rework cycle 2,
 * HIGH #1). S5 is reached from the sidebar footer from ANY section: [apriModelli] also selects the
 * Registrazioni section (which hosts S5), so the footer never "does nothing" from Parlanti.
 */
@Stable
internal class NavigazioneProgetto(private val azioni: AzioniShell, iniziale: SchermataR1) {
    var schermata: SchermataR1 by mutableStateOf(iniziale)
        private set

    fun apriRegistrazione(id: RegistrazioneId) {
        schermata = SchermataR1.Registrazione(id)
    }

    /** The sidebar's reset hook: back to the S2 list (re-click of 'Registrazioni', or leaving S5). */
    fun tornaAllElenco() {
        schermata = SchermataR1.Registrazioni
    }

    fun apriModelli() {
        azioni.seleziona(DestinazioneShell.REGISTRAZIONI)
        schermata = SchermataR1.Modelli
    }

    /** True while S5 is on screen — the sidebar then highlights its footer, no nav item. */
    fun modelliMostrati(stato: ShellUiStato): Boolean =
        stato is ShellUiStato.ConProgetto &&
            stato.destinazioneSelezionata == DestinazioneShell.REGISTRAZIONI &&
            schermata == SchermataR1.Modelli
}

/**
 * The content area of an open project: [parlanti] (R2 only) for the Parlanti section, otherwise the
 * Registrazioni section's current [NavigazioneProgetto.schermata]. Slots only — the presenters are
 * remembered by the caller ABOVE this switch, so moving between places keeps their state.
 */
@Suppress("LongParameterList") // one slot per place of the content area
@Composable
internal fun ContenutoProgetto(
    conProgetto: ShellUiStato.ConProgetto,
    navigazione: NavigazioneProgetto,
    elenco: @Composable () -> Unit,
    registrazione: @Composable (RegistrazioneId) -> Unit,
    modelli: @Composable () -> Unit,
    parlanti: (@Composable () -> Unit)? = null,
) {
    if (parlanti != null && conProgetto.destinazioneSelezionata == DestinazioneShell.PARLANTI) {
        parlanti()
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        when (val s = navigazione.schermata) {
            SchermataR1.Registrazioni -> elenco()
            is SchermataR1.Registrazione -> registrazione(s.id)
            SchermataR1.Modelli -> modelli()
        }
    }
}

/**
 * The shell wired to the project's [NavigazioneProgetto] (one per open project, [iniziale] read when it
 * opens): the footer opens S5 from any section, the sidebar's reset hook returns to the S2 list, and
 * while S5 is shown the footer — not a nav item — is the highlighted place.
 */
@Composable
internal fun ShellProgetto(
    shellPresenter: ShellPresenter,
    iniziale: () -> SchermataR1,
    contenutoSenzaProgetto: @Composable () -> Unit,
    contenuto: @Composable (ShellUiStato.ConProgetto, NavigazioneProgetto) -> Unit,
) {
    val stato by shellPresenter.stato.collectAsState()
    val progettoId = (stato as? ShellUiStato.ConProgetto)?.progetto?.progettoId
    val navigazione = remember(progettoId) { NavigazioneProgetto(shellPresenter.azioni, iniziale()) }
    ShellRoute(
        presenter = shellPresenter,
        contenutoSenzaProgetto = contenutoSenzaProgetto,
        contenuto = { conProgetto -> contenuto(conProgetto, navigazione) },
        onRegistrazioniSelezionata = navigazione::tornaAllElenco,
        onModelliELicenze = navigazione::apriModelli,
        modelliSelezionati = navigazione.modelliMostrati(stato),
    )
}
