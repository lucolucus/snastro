package snastro.avvio.r3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.r1.ContenutoProgetto
import snastro.avvio.r1.SchermataR1
import snastro.avvio.r1.ShellProgetto
import snastro.avvio.r2.GrafoR2
import snastro.avvio.r2.SEZIONI_SHELL_R2
import snastro.avvio.r2.costruisciParlantiPresenter
import snastro.avvio.r2.costruisciRegistrazionePresenterR2
import snastro.avvio.r2.costruisciRegistrazioniPresenterR2
import snastro.kernel.RegistrazioneId
import snastro.ui.ShellPresenter
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.StatoModelli
import snastro.ui.parlanti.ParlantiRoute
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazione.SorgenteRiassuntoS3
import snastro.ui.registrazioni.RegistrazioniRoute
import snastro.ui.riassunto.RiassuntoPresenter
import snastro.ui.riassunto.SchedaRiassunto
import snastro.ui.riassunto.segnoRiassunto

/**
 * R3's app content (ADR 0021 §10): R2's — S1; with a project S2/S3/S4/S5, the same presenters built by the same R2
 * builders — plus the Riassunto tab on S3. ONE [SelezioneSchedaS3] per window (AC-S121, carry-over 3), remembered at
 * this root and shared by every S3 presenter, so the chosen tab survives navigating between recordings.
 */
@Composable
internal fun ContenutoAppR3(grafo: GrafoR2, sceltaCartella: SceltaCartella) {
    val r0 = grafo.r0
    val shellPresenter = remember { ShellPresenter(r0.scope, r0.io, r0.sessione, SEZIONI_SHELL_R2) }
    val modelliPresenter = remember { ModelliPresenter(r0.scope, r0.io, grafo.servizioModelli) }
    val selezioneScheda = remember { SelezioneSchedaS3() }
    val iniziale = {
        if (grafo.servizioModelli.stato.value == StatoModelli.Pronti) SchermataR1.Registrazioni else SchermataR1.Modelli
    }
    ShellProgetto(
        shellPresenter = shellPresenter,
        iniziale = iniziale,
        contenutoSenzaProgetto = {
            val progettiPresenter = remember { ProgettiPresenter(r0.scope, r0.io, r0.elencoProgetti, r0.sessione) }
            ProgettiRoute(progettiPresenter, r0.cartellaProgettiPredefinita, sceltaCartella)
        },
        contenuto = { conProgetto, navigazione ->
            val collaboratori = r0.sessione.collaboratoriCorrenti()
            val r3 = collaboratori?.estensione as? CollaboratoriR3
            if (collaboratori != null && r3 != null) {
                val r2 = r3.r2
                val progettoId = conProgetto.progetto.progettoId
                val registrazioniPresenter = remember(progettoId) {
                    costruisciRegistrazioniPresenterR2(r0, collaboratori, r2) { id ->
                        navigazione.apriRegistrazione(id)
                    }
                }
                val parlantiPresenter = remember(progettoId) { costruisciParlantiPresenter(r0, collaboratori, r2) }
                DisposableEffect(r2, navigazione) {
                    r2.pulizia.dimenticaPosto = { id -> collaboratori.scope.launch { navigazione.dimentica(id) } }
                    onDispose { r2.pulizia.dimenticaPosto = {} }
                }
                ContenutoProgetto(
                    conProgetto = conProgetto,
                    navigazione = navigazione,
                    elenco = { RegistrazioniRoute(registrazioniPresenter) },
                    registrazione = { id -> SchermataRegistrazioneR3(grafo, collaboratori, r3, id, selezioneScheda) },
                    modelli = { ModelliRoute(modelliPresenter) },
                    parlanti = { ParlantiRoute(parlantiPresenter) },
                )
            }
        },
        etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede,
    )
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
