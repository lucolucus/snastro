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
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.etichetta
import snastro.ui.testi.messaggioPer
import java.io.File
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
 * inline message). M1: a refresh MERGES the freshly-read rows into the current [RegistrazioniUiStato]
 * instead of rebuilding it from scratch, so a refresh landing mid-flight of an unrelated in-progress
 * operation never wipes [RegistrazioniUiStato.Dati.importoInCorso]/`errore` or a row's
 * `operazioneInCorso`/`erroreRiga`; a generation counter drops a [carica] result that resolves after a
 * newer one already has (out-of-order completion). M5: the INITIAL load failing (no rows known yet)
 * is a distinct [RegistrazioniUiStato.Errore] with a retry action, never the misleading AC-199 empty
 * message.
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

    // M1: bumped at the START of every `carica()`; a result is applied only if this is still the
    // latest call when it resolves — drops a stale (superseded) result instead of letting it overwrite
    // a fresher one on out-of-order completion. Mutated only from `scope`'s own (confined) dispatcher.
    private var generazioneCaricamento = 0

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        try {
            val righe = withContext(io) { costruisciRighe() }
            if (generazione == generazioneCaricamento) aggiornaConNuoveRighe(righe)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Wraps the read-model load in error handling (lesson from schermata-progetti/lettore-audio
            // reviews): the previous good rows (if any) stay on screen, H1 — never stuck in Caricamento.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            if (generazione == generazioneCaricamento) segnalaErroreDiCaricamento()
        }
    }

    /**
     * M1: merges freshly-read [nuove] rows into the CURRENT [RegistrazioniUiStato.Dati] instead of
     * rebuilding it — [RegistrazioniUiStato.Dati.importoInCorso]/`errore` and each row's
     * `operazioneInCorso`/`erroreRiga` survive; only their OWNING action ([importa]/[suRiga]) ever
     * clears them.
     */
    private fun aggiornaConNuoveRighe(nuove: List<RigaRegistrazione>) {
        val precedente = _stato.value as? RegistrazioniUiStato.Dati
        val fuse = nuove.map { nuova ->
            val vecchia = precedente?.righe?.find { it.registrazioneId == nuova.registrazioneId }
            if (vecchia == null) {
                nuova
            } else {
                nuova.copy(operazioneInCorso = vecchia.operazioneInCorso, erroreRiga = vecchia.erroreRiga)
            }
        }
        _stato.value = RegistrazioniUiStato.Dati(
            righe = fuse,
            importoInCorso = precedente?.importoInCorso ?: false,
            errore = precedente?.errore,
        )
    }

    /**
     * M5: the INITIAL load (no [RegistrazioniUiStato.Dati] known yet) fails into a distinct
     * [RegistrazioniUiStato.Errore] with a retry action — never the misleading AC-199 empty-list
     * message. A later refresh failure (already showing [RegistrazioniUiStato.Dati]) keeps the known
     * rows and every in-flight flag on screen (H1), only [RegistrazioniUiStato.Dati.errore] changes.
     */
    private fun segnalaErroreDiCaricamento() {
        when (val attuale = _stato.value) {
            is RegistrazioniUiStato.Dati -> _stato.value = attuale.copy(errore = MESSAGGIO_ERRORE_GENERICO)
            RegistrazioniUiStato.Caricamento, is RegistrazioniUiStato.Errore ->
                _stato.value = RegistrazioniUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO)
        }
    }

    /** M5: retries the initial load after [RegistrazioniUiStato.Errore]. */
    fun riprova() {
        scope.launch { carica() }
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

    /**
     * AC-199..201/LOW: drag-and-drop (one or more files) and the file picker (one file, as
     * `listOf(path)`) both land here. Every file is imported SEQUENTIALLY — a drop no longer silently
     * drops every file after the first — and every per-file failure is collected and reported inline
     * together, without stopping the others; the list is refreshed once at the end.
     */
    fun importa(percorsi: List<String>) {
        if (percorsi.isEmpty()) return
        val attuale = _stato.value
        if (attuale !is RegistrazioniUiStato.Dati || attuale.importoInCorso) return // M3
        _stato.value = attuale.copy(importoInCorso = true, errore = null)
        scope.launch {
            val errori = mutableListOf<String>()
            for (percorso in percorsi) {
                try {
                    when (val esito = withContext(io) { aggiungiRegistrazione(AggiungiRegistrazione(percorso)) }) {
                        is Esito.Ok -> {}
                        is Esito.Errore -> errori += messaggioImportFallito(percorso, messaggioPer(esito.errore))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    errori += messaggioImportFallito(percorso, MESSAGGIO_ERRORE_GENERICO)
                }
            }
            carica() // M1: merges the refreshed list, preserving importoInCorso/errore until reset below
            val messaggioErrori = errori.takeIf(List<*>::isNotEmpty)?.joinToString("\n")
            aggiornaDati { it.copy(importoInCorso = false, errore = messaggioErrori) }
        }
    }

    private fun messaggioImportFallito(percorso: String, messaggio: String): String =
        "${File(percorso).name}: $messaggio"

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
                    is Esito.Ok -> {
                        carica() // M1: merges the refreshed list, preserving this row's flags until reset below
                        aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = null) }
                    }
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

    /**
     * AC-343: always `daMs = 0` — 'restart from the beginning', never a resume from `posizioneMs`.
     * H2: a throwing [LettoreAudio] is mapped to an inline row error, never left to kill this
     * coroutine — [scope] has no supervisor, so an uncaught exception here would also take down the
     * `stato`/`AggiornamentiVista`/`LettoreAudio.stato` collectors launched in `init`.
     */
    fun riproduci(id: RegistrazioneId) {
        scope.launch {
            try {
                withContext(io) { lettore.riproduciDa(id, 0) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** AC-343: no target — pauses whichever row `LettoreAudio.stato` currently plays. H2: same
     * exception safety as [riproduci]; the active row (if any) is captured before the call so a
     * failure can still land on it. */
    fun pausa() {
        val idAttivo = lettore.stato.value.registrazioneId
        scope.launch {
            try {
                withContext(io) { lettore.pausa() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                idAttivo?.let { id -> aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) } }
            }
        }
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
        riprova = ::riprova,
    )
}
