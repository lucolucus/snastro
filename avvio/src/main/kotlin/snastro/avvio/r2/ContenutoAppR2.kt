package snastro.avvio.r2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.GrafoR0
import snastro.avvio.r1.ContenutoProgetto
import snastro.avvio.r1.SchermataR1
import snastro.avvio.r1.ShellProgetto
import snastro.kernel.RegistrazioneId
import snastro.kernel.mappa
import snastro.ui.ShellPresenter
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ModelliRoute
import snastro.ui.modelli.StatoModelli
import snastro.ui.parlanti.ParlantiPresenter
import snastro.ui.parlanti.ParlantiRoute
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiRoute
import snastro.ui.registrazione.RegistrazionePresenter
import snastro.ui.registrazione.RegistrazioneRoute
import snastro.ui.registrazione.SorgentiParlanti
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniRoute

/**
 * R2's app content: R1's (S1; with a project, S2/S3/S5 — S5 reachable from the shell's sidebar footer,
 * rework cycle 1, HIGH #9: the former standalone top bar is gone) plus the shell's Parlanti section,
 * S4 (AC-341/AC-177). S2 carries the identification badge (AC-204/AC-345), S3 the Voci panel and the
 * Revisione UI (AC-402: its [SorgentiParlanti]). The Registrazioni section's own place (S2/S3/S5) and
 * every presenter are remembered per project ABOVE the section switch, so going to Parlanti and back
 * keeps where the user was (re-clicking the sidebar's OWN 'Registrazioni' item, already selected,
 * still jumps back to its list). Rework cycle 2 (HIGH #1): the footer opens S5 from ANY section and any
 * nav click leaves it — [ShellProgetto]/[snastro.avvio.r1.NavigazioneProgetto].
 */
@Composable
internal fun ContenutoAppR2(grafo: GrafoR2) {
    val r0 = grafo.r0
    val shellPresenter = remember { ShellPresenter(r0.scope, r0.io, r0.sessione, SEZIONI_SHELL_R2) }
    val modelliPresenter = remember { ModelliPresenter(r0.scope, r0.io, grafo.servizioModelli) }
    val iniziale = {
        if (grafo.servizioModelli.stato.value == StatoModelli.Pronti) SchermataR1.Registrazioni else SchermataR1.Modelli
    }
    ShellProgetto(
        shellPresenter = shellPresenter,
        iniziale = iniziale,
        contenutoSenzaProgetto = {
            val progettiPresenter = remember { ProgettiPresenter(r0.scope, r0.io, r0.elencoProgetti, r0.sessione) }
            ProgettiRoute(progettiPresenter, r0.cartellaProgettiPredefinita)
        },
        contenuto = { conProgetto, navigazione ->
            val collaboratori = r0.sessione.collaboratoriCorrenti()
            val r2 = collaboratori?.estensione as? CollaboratoriR2
            if (collaboratori != null && r2 != null) {
                val progettoId = conProgetto.progetto.progettoId
                val registrazioniPresenter = remember(progettoId) {
                    costruisciRegistrazioniPresenterR2(r0, collaboratori, r2) { id ->
                        navigazione.apriRegistrazione(id)
                    }
                }
                val parlantiPresenter = remember(progettoId) { costruisciParlantiPresenter(r0, collaboratori, r2) }
                ContenutoProgetto(
                    conProgetto = conProgetto,
                    navigazione = navigazione,
                    elenco = { RegistrazioniRoute(registrazioniPresenter) },
                    registrazione = { id -> SchermataRegistrazioneR2(grafo, collaboratori, r2, id) },
                    modelli = { ModelliRoute(modelliPresenter) },
                    parlanti = { ParlantiRoute(parlantiPresenter) },
                )
            }
        },
    )
}

/**
 * S3 of [id] on its OWN screen scope — a child of the project's R2 job ([CollaboratoriR2.scopeSchermata]),
 * cancelled when S3 leaves composition or shows another Registrazione: its ONE Proposta job goes with it
 * (AC-421), while a pending card command lives on in the project scope (AC-418). Closing the project
 * cancels and JOINS it before the database closes (AC-420).
 */
@Composable
private fun SchermataRegistrazioneR2(
    grafo: GrafoR2,
    collaboratori: CollaboratoriProgettoAperto,
    r2: CollaboratoriR2,
    id: RegistrazioneId,
) {
    val scopeS3 = remember(id) { r2.scopeSchermata(collaboratori.scope) }
    DisposableEffect(scopeS3) { onDispose { scopeS3.cancel() } }
    val presenter = remember(id) { costruisciRegistrazionePresenterR2(grafo, collaboratori, r2, id, scopeS3) }
    RegistrazioneRoute(presenter)
}

/**
 * S2 as R1's (AC-355, 'Annulla' AC-478) plus the identification badge ([LettureParlanti.identificazioni],
 * AC-204/AC-345) and 'Ritrascrivi' (ADR 0018, AC-457): offered only here, by the composition that registers
 * the synchronous Parlanti purge (`EstensioneR2`). It is R1's own `AvviaElaborazione` over
 * `eventi.unitaDiLavoro`, which also nudges the queue — through [CollaboratoriR2.avviaElaborazione], which then
 * drops that Registrazione's similarity computation or preview (AC-537).
 */
internal fun costruisciRegistrazioniPresenterR2(
    grafo: GrafoR0,
    collaboratori: CollaboratoriProgettoAperto,
    r2: CollaboratoriR2,
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
    statiElaborazione = r2.r1.statiElaborazione,
    avviaElaborazione = r2::avviaElaborazione,
    apriRegistrazione = apriRegistrazione,
    identificazioni = r2.letture.identificazioni,
    ritrascrivi = r2::avviaElaborazione,
    annullaElaborazione = r2.r1.annullaElaborazione,
)

/**
 * S3 with the R2 sources: the Voci panel, the Nome labels, the card commands (AC-418) and namings (ADR 0019
 * §5), 'Togli conferma', 'Riassegna per somiglianza' (the per-project [AzioniSomiglianzaProgetto]), the
 * Revisione UI; plus
 * the latest run's state of [id] and the project's AggiornamentiVista (ADR 0018 Amendment (b) §2): READ-ONLY
 * while a re-run is queued or running, editable again on the Cambiamento that ends it (AC-461).
 */
internal fun costruisciRegistrazionePresenterR2(
    grafo: GrafoR2,
    collaboratori: CollaboratoriProgettoAperto,
    r2: CollaboratoriR2,
    id: RegistrazioneId,
    scope: CoroutineScope,
): RegistrazionePresenter = RegistrazionePresenter(
    scope = scope,
    io = grafo.r0.io,
    registrazioneId = id,
    trascritto = { r2.r1.trascritto(id) },
    documento = { r2.r1.percorsoDocumento(id) },
    lettore = collaboratori.lettoreAudio,
    apriEsterno = grafo.apriEsterno,
    parlanti = SorgentiParlanti(
        identificazione = { r2.letture.identificazione(id) },
        proposta = r2.letture.proposta,
        unioni = { r2.letture.unioni(id) },
        parlantiAttivi = r2.letture.parlantiAttivi,
        estratto = r2.letture.estratto,
        comandi = r2.comandi,
        unisci = r2.r1.revisione.unisciVoci::esegui,
        dividi = r2.r1.revisione.dividiVoce::esegui,
        riassegna = { r2.r1.revisione.riassegnaSegmento.esegui(it).mappa { } },
        aggiornamenti = collaboratori.aggiornamentiVista,
        clock = grafo.r0.clock,
        confermaSegmento = r2.confermaSegmento,
        somiglianza = r2.somiglianza,
    ),
    stati = { r2.r1.statiElaborazione(listOf(id)).firstOrNull() },
    aggiornamenti = collaboratori.aggiornamentiVista,
)

/** S4 · Parlanti del Progetto on the project's session scope (as S2, H2). */
internal fun costruisciParlantiPresenter(
    grafo: GrafoR0,
    collaboratori: CollaboratoriProgettoAperto,
    r2: CollaboratoriR2,
): ParlantiPresenter = ParlantiPresenter(
    scope = collaboratori.scope,
    io = grafo.io,
    parlanti = r2.letture.parlantiDelProgetto,
    rinominaParlante = r2.comandiParlante.rinomina,
    promuoviParlante = r2.comandiParlante.promuovi,
    eliminaParlante = r2.comandiParlante.elimina,
    lettore = collaboratori.lettoreAudio,
    aggiornamenti = collaboratori.aggiornamentiVista,
)
