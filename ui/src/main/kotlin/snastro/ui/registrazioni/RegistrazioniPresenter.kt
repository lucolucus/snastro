package snastro.ui.registrazioni

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.etichetta
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * State holder of S2 · Registrazioni del Progetto (RC-2, thin UI): joins `registrazioni-del-progetto`
 * with `stati-elaborazione` by [RegistrazioneId] (R1, AC-342 — [statiElaborazione]/[avviaElaborazione]
 * are `null` in R0: no status column, no 'Trascrivi'/'Riprova', row click does nothing), keeps a
 * per-row reflection of the shared [LettoreAudio] (AC-343: [LettoreAudio.disponibile] is checked once
 * per refresh, [LettoreAudio.stato] is collected live so the play/pause control never goes stale) and
 * refreshes on [AggiornamentiVista] (R15) or after a successful import/`modificaData`/
 * `avviaElaborazione` (never after a failure — H1, AC-201/AC-206/AC-344: "nulla cambia" beyond the
 * inline message).
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([registrazioni]/[aggiungiRegistrazione]/
 * [modificaDataRegistrazione]/[statiElaborazione]/[avviaElaborazione]) rather than the concrete
 * read-model/service classes: `:avvio` binds the real shape (`RegistrazioniDelProgetto::delProgetto`,
 * `<Comando>Servizio::esegui`). This keeps every one of this presenter's own test doubles a plain
 * lambda over Published-Language DTOs (`RegistrazioneDelProgettoVista`, `StatoRegistrazioneVista`,
 * `Esito`) — `:ui` never needs a `*:dominio` aggregate to build a fixture for them (CR-1(b): a
 * `RegistrazioneRepositoryFinta`/`Registrazione.aggiungi` pair would require importing
 * `snastro.progetto.dominio.Registrazione`, off-limits here even from a test file, since the CR-1
 * Konsist rule scans by package, not by source set).
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class RegistrazioniPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    private val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    private val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
    private val clock: Clock,
    private val statiElaborazione: ((List<RegistrazioneId>) -> List<StatoRegistrazioneVista>)? = null,
    private val avviaElaborazione: ((AvviaElaborazione) -> Esito<Unit>)? = null,
    private val apriRegistrazione: (RegistrazioneId) -> Unit = {},
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RegistrazioniUiStato>(RegistrazioniUiStato.Caricamento)
    val stato: StateFlow<RegistrazioniUiStato> = _stato.asStateFlow()

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    private suspend fun carica() {
        try {
            val righe = withContext(io) { costruisciRighe() }
            _stato.value = RegistrazioniUiStato.Dati(righe)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Wraps the read-model load in error handling (lesson from schermata-progetti/lettore-audio
            // reviews): the previous good rows (if any) stay on screen, H1 — never stuck in Caricamento.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            val precedenti = (_stato.value as? RegistrazioniUiStato.Dati)?.righe.orEmpty()
            _stato.value = RegistrazioniUiStato.Dati(precedenti, errore = MESSAGGIO_ERRORE_GENERICO)
        }
    }

    private fun costruisciRighe(): List<RigaRegistrazione> {
        val progetto = registrazioni()
        val stati = statiElaborazione?.invoke(progetto.map { it.registrazioneId })?.associateBy { it.registrazioneId }
        val statoLettore = lettore.stato.value
        return progetto.map { r ->
            RigaRegistrazione(
                registrazioneId = r.registrazioneId,
                titolo = r.titolo,
                dataRegistrazione = r.dataRegistrazione,
                durataMs = r.durataMs,
                elaborazione = stati?.get(r.registrazioneId)?.let(::elaborazioneDi),
                riproduzione = riproduzioneDi(
                    r.registrazioneId,
                    statoLettore,
                    disponibile = lettore.disponibile(r.registrazioneId),
                ),
            )
        }
    }

    private fun elaborazioneDi(v: StatoRegistrazioneVista): StatoElaborazioneRiga = when (v.stato) {
        StatoElaborazioneVista.NON_AVVIATA -> StatoElaborazioneRiga.NonAvviata
        StatoElaborazioneVista.IN_ATTESA -> StatoElaborazioneRiga.InAttesa(v.posizioneInCoda ?: 0)
        StatoElaborazioneVista.IN_CORSO -> StatoElaborazioneRiga.InCorso(
            faseEtichetta = v.fase?.let(::etichetta).orEmpty(),
            trascorsoMs = v.avviataAlle
                ?.let { Duration.between(it, clock.instant()).toMillis().coerceAtLeast(0) }
                ?: 0,
        )
        StatoElaborazioneVista.FALLITA -> StatoElaborazioneRiga.Fallita(v.motivoFallimento.orEmpty())
        StatoElaborazioneVista.COMPLETATA -> StatoElaborazioneRiga.Completata
    }

    private fun riproduzioneDi(
        id: RegistrazioneId,
        s: StatoLettore,
        disponibile: Boolean,
    ): StatoRiproduzioneRiga = when {
        !disponibile -> StatoRiproduzioneRiga.NonDisponibile
        s.registrazioneId == id && s.inRiproduzione -> StatoRiproduzioneRiga.InRiproduzione
        else -> StatoRiproduzioneRiga.Disponibile
    }

    // AC-343: reflects the shared player's live state without re-querying `disponibile` (checked once
    // per `carica`, sticky here — a row already found unavailable stays disabled between refreshes).
    private fun rifletti(s: StatoLettore) = aggiornaDati { dati ->
        dati.copy(
            righe = dati.righe.map { riga ->
                if (riga.riproduzione == StatoRiproduzioneRiga.NonDisponibile) {
                    riga
                } else {
                    riga.copy(riproduzione = riproduzioneDi(riga.registrazioneId, s, disponibile = true))
                }
            },
        )
    }

    /** AC-199..201: drag-and-drop and the file picker both hand their chosen path here. */
    fun importa(percorsoSorgente: String) {
        val attuale = _stato.value
        if (attuale !is RegistrazioniUiStato.Dati || attuale.importoInCorso) return // M3
        _stato.value = attuale.copy(importoInCorso = true, errore = null)
        scope.launch {
            try {
                when (val esito = withContext(io) { aggiungiRegistrazione(AggiungiRegistrazione(percorsoSorgente)) }) {
                    is Esito.Ok -> carica() // refreshes the list; also resets importoInCorso (fresh Dati)
                    is Esito.Errore ->
                        aggiornaDati { it.copy(importoInCorso = false, errore = messaggioPer(esito.errore)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaDati { it.copy(importoInCorso = false, errore = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** AC-206: replaces the DataRegistrazione shown/edited inline on the row. */
    fun modificaData(id: RegistrazioneId, nuovaData: LocalDate) =
        suRiga(id) { withContext(io) { modificaDataRegistrazione(ModificaDataRegistrazione(id, nuovaData)) } }

    /** AC-344/AC-203: 'Trascrivi' (NON_AVVIATA) and 'Riprova' (FALLITA) both land here. */
    fun avviaElaborazione(id: RegistrazioneId) {
        val comando = avviaElaborazione ?: return // R0: the button isn't rendered either (elaborazione == null)
        suRiga(id) { withContext(io) { comando(AvviaElaborazione(id)) } }
    }

    private fun suRiga(id: RegistrazioneId, operazione: suspend () -> Esito<Unit>) {
        val riga = (_stato.value as? RegistrazioniUiStato.Dati)?.righe?.find { it.registrazioneId == id } ?: return
        if (riga.operazioneInCorso) return // M3
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        scope.launch {
            try {
                when (val esito = operazione()) {
                    is Esito.Ok -> carica()
                    is Esito.Errore ->
                        aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = messaggioPer(esito.errore)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** AC-343: always `daMs = 0` — 'restart from the beginning', never a resume from `posizioneMs`. */
    fun riproduci(id: RegistrazioneId) {
        scope.launch { withContext(io) { lettore.riproduciDa(id, 0) } }
    }

    /** AC-343: no target — pauses whichever row `LettoreAudio.stato` currently plays. */
    fun pausa() {
        scope.launch { withContext(io) { lettore.pausa() } }
    }

    /** AC-203/AC-342: only a COMPLETATA row opens S3 — a click elsewhere (or in R0) is a no-op. */
    fun apriRiga(id: RegistrazioneId) {
        val dati = _stato.value as? RegistrazioniUiStato.Dati ?: return
        val riga = dati.righe.find { it.registrazioneId == id } ?: return
        if (riga.elaborazione == StatoElaborazioneRiga.Completata) apriRegistrazione(id)
    }

    /** H1: dismisses the current list-level `errore` (import/refresh), if any. */
    fun chiudiErrore() = aggiornaDati { it.copy(errore = null) }

    /** H1: dismisses the current `erroreRiga` of [id], if any. */
    fun chiudiErroreRiga(id: RegistrazioneId) = aggiornaRiga(id) { it.copy(erroreRiga = null) }

    private fun aggiornaDati(f: (RegistrazioniUiStato.Dati) -> RegistrazioniUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is RegistrazioniUiStato.Dati) _stato.value = f(attuale)
    }

    private fun aggiornaRiga(id: RegistrazioneId, f: (RigaRegistrazione) -> RigaRegistrazione) =
        aggiornaDati { dati -> dati.copy(righe = dati.righe.map { if (it.registrazioneId == id) f(it) else it }) }

    val azioni: AzioniRegistrazioni = AzioniRegistrazioni(
        importa = ::importa,
        modificaData = ::modificaData,
        riproduci = ::riproduci,
        pausa = ::pausa,
        avviaElaborazione = ::avviaElaborazione,
        apriRiga = ::apriRiga,
        chiudiErrore = ::chiudiErrore,
        chiudiErroreRiga = ::chiudiErroreRiga,
    )
}
