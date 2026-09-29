package snastro.avvio.r3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.r1.ContenutoAppCondiviso
import snastro.avvio.r2.ContenutoProgettoR2
import snastro.avvio.r2.GrafoR2
import snastro.avvio.r2.SEZIONI_SHELL_R2
import snastro.avvio.r2.costruisciRegistrazionePresenterR2
import snastro.kernel.RegistrazioneId
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazione.SorgenteRiassuntoS3
import snastro.ui.riassunto.RiassuntoPresenter
import snastro.ui.riassunto.SchedaRiassunto
import snastro.ui.riassunto.segnoRiassunto

/**
 * R3's app content (ADR 0021 §10): R2's — S1; with a project S2/S3/S4/S5, the same presenters built by the same R2
 * builders — plus the Riassunto tab on S3. ONE [SelezioneSchedaS3] per window (AC-S121, carry-over 3), remembered at
 * this root and shared by every S3 presenter, so the chosen tab survives navigating between recordings. AC-C65: this
 * function only passes [SEZIONI_SHELL_R2] (via [ContenutoAppCondiviso], R1/R2/R3's shared body), its own
 * [CollaboratoriR3] extension and the S3 builder; [ContenutoProgettoR2] is R2's and R3's shared content (the
 * "near-copy" pre-release.md called out), reused here over `r3.r2`.
 */
@Composable
internal fun ContenutoAppR3(grafo: GrafoR2, sceltaCartella: SceltaCartella) {
    val r0 = grafo.r0
    val selezioneScheda = remember { SelezioneSchedaS3() }
    ContenutoAppCondiviso(r0, grafo.servizioModelli, SEZIONI_SHELL_R2, sceltaCartella) {
            conProgetto,
            navigazione,
            collaboratori,
            modelliPresenter,
        ->
        val r3 = collaboratori.estensione as? CollaboratoriR3
        if (r3 != null) {
            ContenutoProgettoR2(r0, conProgetto, navigazione, collaboratori, r3.r2, modelliPresenter) { id ->
                SchermataRegistrazioneR3(grafo, collaboratori, r3, id, selezioneScheda)
            }
        }
    }
}

/**
 * S3 of [id] as in R2 (its own screen scope, a child of the project's R2 job) plus the Riassunto tab. The tab's
 * [RiassuntoPresenter] is HOISTED here, keyed by [id] on S3's own scope (carry-over 5): switching Trascrizione ↔
 * Riassunto keeps the typed Argomento and an open editor, and another recording gets a fresh presenter while the
 * old one's coroutines are cancelled with the old scope.
 */
@Composable
private fun SchermataRegistrazioneR3(
    grafo: GrafoR2,
    collaboratori: CollaboratoriProgettoAperto,
    r3: CollaboratoriR3,
    id: RegistrazioneId,
    selezioneScheda: SelezioneSchedaS3,
) {
    val scopeS3 = remember(id) { r3.r2.scopeSchermata(collaboratori.scope) }
    DisposableEffect(scopeS3) { onDispose { scopeS3.cancel() } }
    val presenter = remember(id) {
        val riassunto = costruisciRiassuntoPresenter(grafo, collaboratori, r3, id, scopeS3)
        costruisciRegistrazionePresenterR2(
            grafo,
            collaboratori,
            r3.r2,
            id,
            scopeS3,
            riassunto = sorgenteRiassunto(grafo, collaboratori, r3, riassunto),
            selezioneSchedaS3 = selezioneScheda,
        )
    }
    RegistrazioneRoute(presenter)
}

/** The Riassunto tab's presenter of [id] (AC-S125..S139): every source bound to this project and Registrazione. */
internal fun costruisciRiassuntoPresenter(
    grafo: GrafoR2,
    collaboratori: CollaboratoriProgettoAperto,
    r3: CollaboratoriR3,
    id: RegistrazioneId,
    scope: CoroutineScope,
): RiassuntoPresenter = RiassuntoPresenter(
    scope = scope,
    io = grafo.r0.io,
    registrazioneId = id,
    vista = { r3.vista(id) },
    impostazioni = r3.impostazioni,
    posizioni = r3.r2.r1.coda::istantanea,
    riassumiCmd = { argomento -> r3.riassumi(id, argomento) },
    modificaLunghezzaMassimaCmd = r3.modificaLunghezzaMassima,
    servizioModelli = grafo.servizioModelli,
    aggiornamenti = collaboratori.aggiornamentiVista,
    clock = grafo.r0.clock,
    idModelloLinguistico = ID_MODELLO_LINGUISTICO,
)

/** `ui-schede-registrazione`'s slot: [presenter]'s tab body, and the tab mark read on its own lifecycle (AC-S122). */
internal fun sorgenteRiassunto(
    grafo: GrafoR2,
    collaboratori: CollaboratoriProgettoAperto,
    r3: CollaboratoriR3,
    presenter: RiassuntoPresenter,
): SorgenteRiassuntoS3 = SorgenteRiassuntoS3(
    contenuto = { _ ->
        val stato by presenter.stato.collectAsState()
        SchedaRiassunto(stato, presenter.azioni)
    },
    segno = { id -> segnoRiassunto(id, grafo.r0.io, r3.vista, collaboratori.aggiornamentiVista) },
)
