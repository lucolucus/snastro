package snastro.avvio

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import snastro.avvio.modelli.DIMENSIONE_MODELLO_LINGUISTICO_BYTE
import snastro.avvio.modelli.ID_MODELLO_LINGUISTICO
import snastro.avvio.progetto.CollaboratoriProgetto
import snastro.kernel.RegistrazioneId
import snastro.kernel.mappa
import snastro.sintesi.dominio.Argomento
import snastro.ui.ShellPresenter
import snastro.ui.impostazioni.ImpostazioniPresenter
import snastro.ui.impostazioni.LunghezzaRiassuntoPresenter
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.parlanti.ParlantiPresenter
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.registrazione.RegistrazionePresenter
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazione.SorgenteRiassuntoS3
import snastro.ui.registrazione.SorgentiParlanti
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.riassunto.RiassuntoPresenter
import snastro.ui.riassunto.SchedaRiassunto
import snastro.ui.riassunto.segnoRiassunto

/*
 * ADR 0030 §1/§4 (c2 carry-over, block c3): the ONE place every presenter `ContenutoApp` shows is built, each over the
 * app [Grafo] and the open project's typed [CollaboratoriProgetto] — no cast, no per-release variant.
 */

/** The shell (S1 ↔ open project), with the app's ONE [SEZIONI_SHELL]. */
internal fun costruisciShellPresenter(grafo: Grafo): ShellPresenter =
    ShellPresenter(grafo.scope, grafo.io, grafo.sessione, SEZIONI_SHELL)

/** S1: the recent projects. */
internal fun costruisciProgettiPresenter(grafo: Grafo): ProgettiPresenter =
    ProgettiPresenter(grafo.scope, grafo.io, grafo.elencoProgetti, grafo.sessione)

/** Impostazioni (app-wide part, one per window): theme and folder of new projects, over [Grafo.preferenze]. */
internal fun costruisciImpostazioniPresenter(grafo: Grafo): ImpostazioniPresenter =
    ImpostazioniPresenter(grafo.scope, grafo.io, grafo.preferenze, grafo.cartellaProgettiPredefinita)

/** Impostazioni › Riassunto of the open project, on its session scope: the same read-model/command as the tab. */
internal fun costruisciLunghezzaRiassuntoPresenter(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
): LunghezzaRiassuntoPresenter = LunghezzaRiassuntoPresenter(
    scope = collaboratori.scope,
    io = grafo.io,
    leggi = collaboratori.sintesi.impostazioni,
    modifica = collaboratori.sintesi.modificaLunghezzaMassima,
)

/** S5 (and the sidebar footer's model line, AC-S163). */
internal fun costruisciModelliPresenter(grafo: Grafo): ModelliPresenter =
    ModelliPresenter(grafo.scope, grafo.io, grafo.servizioModelli)

/**
 * S2 on the project's session scope (H2): the Trascrizione sources (AC-355: status, 'Trascrivi'/'Riprova',
 * 'Annulla' on a queued row AC-478, the queue positions ADR 0023 §4), the identification badge (AC-204/AC-345),
 * 'Ritrascrivi' (ADR 0018, AC-457: the composition registers the synchronous Parlanti purge) and 'Elimina…'
 * (ADR 0020, AC-630: it registers the synchronous purges: Sintesi and Trascrizione on `RegistrazioneEliminata`,
 * Parlanti's on the `TrascrittoEliminato` the latter publishes). A row with a Trascritto
 * opens S3 ([apriRegistrazione]).
 */
internal fun costruisciRegistrazioniPresenter(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    apriRegistrazione: (RegistrazioneId) -> Unit,
): RegistrazioniPresenter = RegistrazioniPresenter(
    scope = collaboratori.scope,
    io = grafo.io,
    progettoId = collaboratori.progettoId,
    registrazioni = collaboratori.registrazioni,
    aggiungiRegistrazione = collaboratori.aggiungiRegistrazione,
    modificaDataRegistrazione = collaboratori.modificaDataRegistrazione,
    rinominaRegistrazione = collaboratori.rinominaRegistrazione,
    lettore = collaboratori.lettoreAudio,
    aggiornamenti = collaboratori.aggiornamentiVista,
    clock = grafo.clock,
    statiElaborazione = collaboratori.trascrizione.statiElaborazione,
    avviaElaborazione = collaboratori.avviaElaborazione,
    apriRegistrazione = apriRegistrazione,
    identificazioni = collaboratori.parlanti.letture.identificazioni,
    ritrascrivi = collaboratori.avviaElaborazione,
    annullaElaborazione = collaboratori.trascrizione.annullaElaborazione,
    eliminaRegistrazione = collaboratori.eliminaRegistrazione,
    posizioniNellaCoda = collaboratori.posizioniNellaCoda::istantanea,
)

/**
 * S3 of [id] on [scope]: the Trascritto, the Sbobinatura's path, the Voci panel, the card commands (AC-418) and
 * namings (ADR 0019 §5), 'Togli conferma', 'Riassegna per somiglianza', the Revisione UI, the latest run's state
 * (READ-ONLY while a re-run is queued or running, AC-461) and the Riassunto tab ([costruisciRiassuntoPresenter],
 * ADR 0021 §10) with the ONE per-window [selezioneSchedaS3] (AC-S121).
 */
internal fun costruisciRegistrazionePresenter(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    id: RegistrazioneId,
    scope: CoroutineScope,
    selezioneSchedaS3: SelezioneSchedaS3,
): RegistrazionePresenter {
    val trascrizione = collaboratori.trascrizione
    val parlanti = collaboratori.parlanti
    val riassunto = costruisciRiassuntoPresenter(grafo, collaboratori, id, scope)
    return RegistrazionePresenter(
        scope = scope,
        io = grafo.io,
        registrazioneId = id,
        trascritto = { trascrizione.trascritto(id) },
        sbobinatura = { collaboratori.sbobinatura.percorsoSbobinatura(id) },
        lettore = collaboratori.lettoreAudio,
        apriEsterno = grafo.apriEsterno,
        parlanti = SorgentiParlanti(
            identificazione = { parlanti.letture.identificazione(id) },
            proposta = parlanti.letture.proposta,
            unioni = { parlanti.letture.unioni(id) },
            parlantiAttivi = parlanti.letture.parlantiAttivi,
            estratto = parlanti.letture.estratto,
            comandi = parlanti.comandi,
            unisci = trascrizione.revisione.unisciVoci::esegui,
            dividi = trascrizione.revisione.dividiVoce::esegui,
            riassegna = { trascrizione.revisione.riassegnaSegmento.esegui(it).mappa { } },
            aggiornamenti = collaboratori.aggiornamentiVista,
            clock = grafo.clock,
            confermaSegmento = trascrizione.confermaSegmento,
            somiglianza = parlanti.somiglianza,
        ),
        stati = { trascrizione.statiElaborazione(listOf(id)).firstOrNull() },
        aggiornamenti = collaboratori.aggiornamentiVista,
        riassunto = sorgenteRiassunto(grafo, collaboratori, riassunto),
        selezioneSchedaS3 = selezioneSchedaS3,
    )
}

/** S4 · Parlanti del Progetto on the project's session scope (as S2, H2). */
internal fun costruisciParlantiPresenter(grafo: Grafo, collaboratori: CollaboratoriProgetto): ParlantiPresenter =
    ParlantiPresenter(
        scope = collaboratori.scope,
        io = grafo.io,
        parlanti = collaboratori.parlanti.letture.parlantiDelProgetto,
        rinominaParlante = collaboratori.parlanti.comandiParlante.rinomina,
        promuoviParlante = collaboratori.parlanti.comandiParlante.promuovi,
        eliminaParlante = collaboratori.parlanti.comandiParlante.elimina,
        lettore = collaboratori.lettoreAudio,
        aggiornamenti = collaboratori.aggiornamentiVista,
    )

/** The Riassunto tab's presenter of [id] (AC-S125..S139): every source bound to this project and Registrazione. */
internal fun costruisciRiassuntoPresenter(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    id: RegistrazioneId,
    scope: CoroutineScope,
): RiassuntoPresenter = RiassuntoPresenter(
    scope = scope,
    io = grafo.io,
    registrazioneId = id,
    vista = { collaboratori.sintesi.vista(id) },
    impostazioni = collaboratori.sintesi.impostazioni,
    posizioni = collaboratori.posizioniNellaCoda::istantanea,
    riassumiCmd = { argomento -> collaboratori.sintesi.riassumi(id, argomento) },
    modificaLunghezzaMassimaCmd = collaboratori.sintesi.modificaLunghezzaMassima,
    servizioModelli = grafo.servizioModelli,
    aggiornamenti = collaboratori.aggiornamentiVista,
    clock = grafo.clock,
    idModelloLinguistico = ID_MODELLO_LINGUISTICO,
    dimensioneModelloLinguisticoByte = DIMENSIONE_MODELLO_LINGUISTICO_BYTE,
    limiteCaratteriArgomento = Argomento.MASSIMO_CARATTERI,
)

/** `ui-schede-registrazione`'s slot: [presenter]'s tab body, and the tab mark read on its own lifecycle (AC-S122). */
internal fun sorgenteRiassunto(
    grafo: Grafo,
    collaboratori: CollaboratoriProgetto,
    presenter: RiassuntoPresenter,
): SorgenteRiassuntoS3 = SorgenteRiassuntoS3(
    contenuto = { _ ->
        val stato by presenter.stato.collectAsState()
        SchedaRiassunto(stato, presenter.azioni)
    },
    segno = { id -> segnoRiassunto(id, grafo.io, collaboratori.sintesi.vista, collaboratori.aggiornamentiVista) },
)
