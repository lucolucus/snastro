package snastro.avvio.r1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.GrafoR0
import snastro.kernel.RegistrazioneId
import snastro.ui.ShellPresenter
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.StatoModelli
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.progetti.SceltaCartella
import snastro.ui.registrazione.RegistrazionePresenter
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniRoute

/**
 * R1's app content: S1 without a project; with one, S2 (with the Trascrizione sources, AC-355), S3 of
 * a completed Registrazione (read-only, opened from S2's row, AC-203) or S5 — reachable from the
 * shell's own sidebar footer (rework cycle 1, HIGH #9: the former standalone 'Registrazioni'/'Modelli'
 * top bar is gone, navigation is the sidebar). S5 opens first while the models are not ready
 * (ux-proposal S5 "When: at startup") — it never blocks the app.
 */
@Composable
internal fun ContenutoAppR1(grafo: GrafoR1, sceltaCartella: SceltaCartella) {
    val r0 = grafo.r0
    val shellPresenter = remember { ShellPresenter(r0.scope, r0.io, r0.sessione, SEZIONI_SHELL_R1) }
    val modelliPresenter = remember { ModelliPresenter(r0.scope, r0.io, grafo.servizioModelli) }
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
            val r1 = collaboratori?.estensione as? CollaboratoriR1
            if (collaboratori != null && r1 != null) {
                val progettoId = conProgetto.progetto.progettoId
                val registrazioniPresenter = remember(progettoId) {
                    costruisciRegistrazioniPresenterR1(r0, collaboratori, r1) { id ->
                        navigazione.apriRegistrazione(id)
                    }
                }
                ContenutoProgetto(
                    conProgetto = conProgetto,
                    navigazione = navigazione,
                    elenco = { RegistrazioniRoute(registrazioniPresenter) },
                    registrazione = { id -> SchermataRegistrazioneR1(grafo, collaboratori, r1, id) },
                    modelli = { ModelliRoute(modelliPresenter) },
                )
            }
        },
    )
}

/**
 * S3 of [id], on its OWN child of the session scope, cancelled when S3 leaves composition — its
 * presenter's collectors never outlive the screen (nor the project: the session scope is their parent).
 */
@Composable
private fun SchermataRegistrazioneR1(
    grafo: GrafoR1,
    collaboratori: CollaboratoriProgettoAperto,
    r1: CollaboratoriR1,
    id: RegistrazioneId,
) {
    val scopeS3 = remember(id) {
        CoroutineScope(collaboratori.scope.coroutineContext + SupervisorJob(collaboratori.scope.coroutineContext[Job]))
    }
    DisposableEffect(scopeS3) { onDispose { scopeS3.cancel() } }
    val presenter = remember(id) { costruisciRegistrazionePresenterR1(grafo, collaboratori, r1, id, scopeS3) }
    RegistrazioneRoute(presenter)
}

/**
 * S2 with the Trascrizione sources (AC-355, AC-342): status column, 'Numero di persone' +
 * 'Trascrivi'/'Riprova' ([CollaboratoriR1.avviaElaborazione]), 'Annulla' on a queued row
 * ([CollaboratoriR1.annullaElaborazione], AC-478), and a row with a Trascritto opening S3
 * ([apriRegistrazione]). No re-run action: R1 registers no Parlanti purge, so it never offers one (AC-460).
 * Launched on the project's own session scope (H2, as R0).
 */
internal fun costruisciRegistrazioniPresenterR1(
    grafo: GrafoR0,
    collaboratori: CollaboratoriProgettoAperto,
    r1: CollaboratoriR1,
    apriRegistrazione: (RegistrazioneId) -> Unit,
): RegistrazioniPresenter = RegistrazioniPresenter(
    scope = collaboratori.scope,
    io = grafo.io,
    registrazioni = collaboratori.registrazioni,
    aggiungiRegistrazione = collaboratori.aggiungiRegistrazione,
    modificaDataRegistrazione = collaboratori.modificaDataRegistrazione,
    rinominaRegistrazione = collaboratori.rinominaRegistrazione,
    lettore = collaboratori.lettoreAudio,
    aggiornamenti = collaboratori.aggiornamentiVista,
    clock = grafo.clock,
    statiElaborazione = r1.statiElaborazione,
    avviaElaborazione = r1::avviaElaborazione,
    apriRegistrazione = apriRegistrazione,
    annullaElaborazione = r1.annullaElaborazione,
)

/**
 * S3, read-only in R1: trascritto-view, the Documento's path, the shared LettoreAudio, ApriEsterno, plus
 * (ADR 0018 Amendment (b)) the latest run's state of [id] and the project's AggiornamentiVista — so S3
 * reloads on its Registrazione's Cambiamenti and follows the read-only rule. No Parlanti sources.
 */
internal fun costruisciRegistrazionePresenterR1(
    grafo: GrafoR1,
    collaboratori: CollaboratoriProgettoAperto,
    r1: CollaboratoriR1,
    id: RegistrazioneId,
    scope: CoroutineScope,
): RegistrazionePresenter = RegistrazionePresenter(
    scope = scope,
    io = grafo.r0.io,
    registrazioneId = id,
    trascritto = { r1.trascritto(id) },
    documento = { r1.percorsoDocumento(id) },
    lettore = collaboratori.lettoreAudio,
    apriEsterno = grafo.apriEsterno,
    stati = { r1.statiElaborazione(listOf(id)).firstOrNull() },
    aggiornamenti = collaboratori.aggiornamentiVista,
)
