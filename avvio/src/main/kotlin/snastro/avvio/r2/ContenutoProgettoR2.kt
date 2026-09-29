package snastro.avvio.r2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.GrafoR0
import snastro.avvio.r1.ContenutoProgetto
import snastro.avvio.r1.NavigazioneProgetto
import snastro.kernel.RegistrazioneId
import snastro.ui.ShellUiStato
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ModelliRoute
import snastro.ui.parlanti.ParlantiRoute
import snastro.ui.registrazioni.RegistrazioniRoute

/**
 * AC-C65 (ADR 0030 §4, block c2-contenuto-app-base): the R2-shaped content area of an open project — the
 * registrazioni list, the Parlanti section, S5 and the after-commit `dimenticaPosto` wiring — shared by
 * `ContenutoAppR2` and `ContenutoAppR3` (pre-release.md: "`ContenutoAppR3` near-copy of `ContenutoAppR2`").
 * R3's extension ([snastro.avvio.r3.CollaboratoriR3]) wraps a [CollaboratoriR2] ([r2]), so it calls this
 * with `r3.r2`. The ONLY real difference between the two releases is the S3 [registrazione] builder (R3
 * additionally hoists the Riassunto tab) — the one parameter this function takes besides the collaborators.
 */
@Suppress("LongParameterList") // one parameter per collaborator this shared content area needs + the S3 builder
@Composable
internal fun ContenutoProgettoR2(
    r0: GrafoR0,
    conProgetto: ShellUiStato.ConProgetto,
    navigazione: NavigazioneProgetto,
    collaboratori: CollaboratoriProgettoAperto,
    r2: CollaboratoriR2,
    modelliPresenter: ModelliPresenter,
    registrazione: @Composable (RegistrazioneId) -> Unit,
) {
    val progettoId = conProgetto.progetto.progettoId
    val registrazioniPresenter = remember(progettoId) {
        costruisciRegistrazioniPresenterR2(r0, collaboratori, r2) { id -> navigazione.apriRegistrazione(id) }
    }
    val parlantiPresenter = remember(progettoId) { costruisciParlantiPresenter(r0, collaboratori, r2) }
    // AC-632 step (4): a deleted Registrazione's S3 place is forgotten, on the UI dispatcher.
    DisposableEffect(r2, navigazione) {
        r2.pulizia.dimenticaPosto = { id -> collaboratori.scope.launch { navigazione.dimentica(id) } }
        onDispose { r2.pulizia.dimenticaPosto = {} }
    }
    ContenutoProgetto(
        conProgetto = conProgetto,
        navigazione = navigazione,
        elenco = { RegistrazioniRoute(registrazioniPresenter) },
        registrazione = registrazione,
        modelli = { ModelliRoute(modelliPresenter) },
        parlanti = { ParlantiRoute(parlantiPresenter) },
    )
}
