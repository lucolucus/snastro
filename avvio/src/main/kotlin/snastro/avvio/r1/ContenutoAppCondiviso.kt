package snastro.avvio.r1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.GrafoR0
import snastro.avvio.costruisciProgettiPresenter
import snastro.avvio.costruisciShellPresenter
import snastro.ui.DestinazioneShell
import snastro.ui.ShellUiStato
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.StatoModelli
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.progetti.SceltaCartella

/** S5 opens first while the models are not ready (ux-proposal S5 "When: at startup") — it never blocks the app. */
internal fun schermataIniziale(servizioModelli: ServizioModelli): SchermataR1 =
    if (servizioModelli.stato.value == StatoModelli.Pronti) SchermataR1.Registrazioni else SchermataR1.Modelli

/** ADR 0030 §4 (block c2): the ONE place [ModelliPresenter] is built, shared by [ContenutoAppCondiviso]. */
internal fun costruisciModelliPresenter(grafo: GrafoR0, servizioModelli: ServizioModelli): ModelliPresenter =
    ModelliPresenter(grafo.scope, grafo.io, servizioModelli)

/**
 * AC-C65 (ADR 0030 §4, block c2-contenuto-app-base): R1/R2/R3's shared `ContenutoApp` body — the ONE place
 * [ShellPresenter][snastro.ui.ShellPresenter], [ModelliPresenter] and
 * [ProgettiPresenter][snastro.ui.progetti.ProgettiPresenter] are constructed and [ShellProgetto] is wired.
 * Parameterised by the real per-release differences only: [sezioniShell] (the shell's own `SEZIONI_SHELL_*`)
 * and [contenuto] — the collaborators' extension (cast + null-check) and the S3 builder are each release's own
 * (`ContenutoAppR1`/`ContenutoAppR2`/`ContenutoAppR3`), since their extension type and presenter builders
 * genuinely differ. R2 and R3 additionally share [ContenutoProgettoR2][snastro.avvio.r2.ContenutoProgettoR2]
 * for the parts THEY have in common (Parlanti, the `dimenticaPosto` effect, `ContenutoProgetto` itself).
 */
@Composable
internal fun ContenutoAppCondiviso(
    grafo: GrafoR0,
    servizioModelli: ServizioModelli,
    sezioniShell: Set<DestinazioneShell>,
    sceltaCartella: SceltaCartella,
    contenuto: @Composable (
        conProgetto: ShellUiStato.ConProgetto,
        navigazione: NavigazioneProgetto,
        collaboratori: CollaboratoriProgettoAperto,
        modelliPresenter: ModelliPresenter,
    ) -> Unit,
) {
    val shellPresenter = remember { costruisciShellPresenter(grafo, sezioniShell) }
    val modelliPresenter = remember { costruisciModelliPresenter(grafo, servizioModelli) }
    ShellProgetto(
        shellPresenter = shellPresenter,
        iniziale = { schermataIniziale(servizioModelli) },
        contenutoSenzaProgetto = {
            val progettiPresenter = remember { costruisciProgettiPresenter(grafo) }
            ProgettiRoute(progettiPresenter, grafo.cartellaProgettiPredefinita, sceltaCartella)
        },
        contenuto = { conProgetto, navigazione ->
            val collaboratori = grafo.sessione.collaboratoriCorrenti()
            if (collaboratori != null) contenuto(conProgetto, navigazione, collaboratori, modelliPresenter)
        },
        etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede,
    )
}
