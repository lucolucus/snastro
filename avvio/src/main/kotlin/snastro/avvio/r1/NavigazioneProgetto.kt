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

    // L755e: the place S5 was opened FROM (S2's list, or S3 of one Registrazione) — `null` when S5 is
    // not showing. Remembered ONLY while `schermata == Modelli`, so a redundant re-click of the
    // footer while already on S5 never overwrites it with `Modelli` itself.
    private var primaDiModelli: SchermataR1? = null

    fun apriRegistrazione(id: RegistrazioneId) {
        schermata = SchermataR1.Registrazione(id)
    }

    /**
     * The sidebar's reset hook, fired for TWO distinct reasons (`SchermataShell`'s own wiring):
     * re-clicking the ALREADY-selected 'Registrazioni' item (always the list's own top, regardless of
     * S5), or leaving S5 via ANY nav click (L755e: restores [primaDiModelli] — S2 or S3, whichever S5
     * was opened from — instead of forcing the list every time, which silently dropped a S3 place).
     */
    fun tornaAllElenco() {
        schermata = if (schermata == SchermataR1.Modelli) {
            primaDiModelli ?: SchermataR1.Registrazioni
        } else {
            SchermataR1.Registrazioni
        }
        primaDiModelli = null
    }

    fun apriModelli() {
        if (schermata != SchermataR1.Modelli) primaDiModelli = schermata
        azioni.seleziona(DestinazioneShell.REGISTRAZIONI)
        schermata = SchermataR1.Modelli
    }

    /**
     * ADR 0020 §5 (AC-632): [id] was deleted — a current place S3 of [id], or S3 of [id] remembered for
     * leaving S5, becomes the S2 list; every other place is untouched. The app never lands on, nor returns
     * to, S3 of a deleted Registrazione.
     */
    fun dimentica(id: RegistrazioneId) {
        val posto = SchermataR1.Registrazione(id)
        if (schermata == posto) schermata = SchermataR1.Registrazioni
        if (primaDiModelli == posto) primaDiModelli = SchermataR1.Registrazioni
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
