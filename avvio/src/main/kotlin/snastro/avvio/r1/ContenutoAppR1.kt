package snastro.avvio.r1

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.GrafoR0
import snastro.kernel.RegistrazioneId
import snastro.ui.ShellPresenter
import snastro.ui.ShellRoute
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.StatoModelli
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.registrazione.RegistrazionePresenter
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniRoute

/**
 * R1's app content: S1 without a project; with one, a thin bar ('Registrazioni' / 'Modelli' — S5 is
 * "reachable from the shell", ux-proposal) over S2 (with the Trascrizione sources, AC-355), S3 of a
 * completed Registrazione (read-only, opened from S2's row, AC-203) or S5. S5 opens first while the
 * models are not ready (ux-proposal S5 "When: at startup") — it never blocks the app.
 */
@Composable
internal fun ContenutoAppR1(grafo: GrafoR1) {
    val r0 = grafo.r0
    val shellPresenter = remember { ShellPresenter(r0.scope, r0.io, r0.sessione, SEZIONI_SHELL_R1) }
    val modelliPresenter = remember { ModelliPresenter(r0.scope, r0.io, grafo.servizioModelli) }
    ShellRoute(
        presenter = shellPresenter,
        contenutoSenzaProgetto = {
            val progettiPresenter = remember { ProgettiPresenter(r0.scope, r0.io, r0.elencoProgetti, r0.sessione) }
            ProgettiRoute(progettiPresenter, r0.cartellaProgettiPredefinita)
        },
        contenuto = { conProgetto ->
            val collaboratori = r0.sessione.collaboratoriCorrenti()
            val r1 = collaboratori?.estensione as? CollaboratoriR1
            if (collaboratori != null && r1 != null) {
                val progettoId = conProgetto.progetto.progettoId
                var schermata by remember(progettoId) {
                    mutableStateOf(
                        if (grafo.servizioModelli.stato.value == StatoModelli.Pronti) {
                            SchermataR1.Registrazioni
                        } else {
                            SchermataR1.Modelli
                        },
                    )
                }
                val registrazioniPresenter = remember(progettoId) {
                    costruisciRegistrazioniPresenterR1(r0, collaboratori, r1) { id ->
                        schermata = SchermataR1.Registrazione(id)
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    BarraR1(
                        onRegistrazioni = { schermata = SchermataR1.Registrazioni },
                        onModelli = { schermata = SchermataR1.Modelli },
                    )
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (val s = schermata) {
                            SchermataR1.Registrazioni -> RegistrazioniRoute(registrazioniPresenter)
                            is SchermataR1.Registrazione -> SchermataRegistrazioneR1(grafo, collaboratori, r1, s.id)
                            SchermataR1.Modelli -> ModelliRoute(modelliPresenter)
                        }
                    }
                }
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

@Composable
internal fun BarraR1(onRegistrazioni: () -> Unit, onModelli: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onRegistrazioni, modifier = Modifier.testTag("avvio-nav-registrazioni")) {
            Text(ETICHETTA_NAV_REGISTRAZIONI)
        }
        TextButton(onClick = onModelli, modifier = Modifier.testTag("avvio-nav-modelli")) {
            Text(ETICHETTA_NAV_MODELLI)
        }
    }
}

/**
 * S2 with the Trascrizione sources (AC-355, AC-342): status column, 'Numero di persone' +
 * 'Trascrivi'/'Riprova' ([CollaboratoriR1.avviaElaborazione]), and a completed row opening S3
 * ([apriRegistrazione]). Launched on the project's own session scope (H2, as R0).
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
)

/** S3, read-only in R1: trascritto-view, the Documento's path, the shared LettoreAudio, ApriEsterno — nothing else. */
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
)

private const val ETICHETTA_NAV_REGISTRAZIONI = "Registrazioni"
private const val ETICHETTA_NAV_MODELLI = "Modelli e licenze"
