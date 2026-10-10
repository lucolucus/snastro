package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.ETICHETTA_REGISTRAZIONE_ELIMINATA
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO

/**
 * State holder of S2 · Registrazioni del Progetto (RC-2, thin UI). The list is read by [LetturaS2] over [LettureS2]
 * (the joins with `stati-elaborazione`, `identificazione-registrazioni` and the queue positions, AC-342/AC-204/AC-345,
 * ADR 0023 §4); this presenter merges it into the screen, keeps a per-row reflection of the shared [LettoreAudio]
 * (AC-343: [LettoreAudio.disponibile] is checked once per refresh, [LettoreAudio.stato] is collected live so the
 * play/pause control never goes stale) and refreshes on [AggiornamentiVista] (R15) or after a successful import or row
 * command (never after a failure — H1, AC-201/AC-206/AC-363/AC-344: "nulla cambia" beyond the inline message).
 * M1: a refresh MERGES the freshly-read rows into the current [RegistrazioniUiStato] instead of rebuilding it from
 * scratch, so a refresh landing mid-flight of an unrelated in-progress operation never wipes
 * [RegistrazioniUiStato.Dati.importoInCorso]/`errore` or a row's `operazioneInCorso`/`erroreRiga`; a generation counter
 * drops a [carica] result that resolves after a newer one already has (out-of-order completion). M5: the INITIAL load
 * failing (no rows known yet) is a distinct [RegistrazioniUiStato.Errore] with a retry action, never the misleading
 * AC-199 empty message.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([LettureS2], [aggiungiRegistrazione] and the row commands'
 * [ComandiRigaS2]) rather than the concrete read-model/service classes: `:avvio` binds the real shape
 * (`RegistrazioniDelProgetto::delProgetto`, `<Comando>Servizio::esegui`). This keeps every one of this presenter's own
 * test doubles a plain lambda over Published-Language DTOs (`RegistrazioneDelProgettoVista`, `StatoRegistrazioneVista`,
 * `Esito`) — `:ui` never needs a `*:dominio` aggregate to build a fixture for them (CR-1(b): a
 * `RegistrazioneRepositoryFinta`/`Registrazione.aggiungi` pair would require importing
 * `snastro.progetto.dominio.Registrazione`, off-limits here even from a test file, since the CR-1 Konsist rule scans by
 * package, not by source set).
 *
 * ADR 0020 §6 ([eliminaRegistrazione]): backs the row's More menu — 'Elimina…' (AC-625/626/627/628) and,
 * on every row where [ComandiRigaS2.ritrascrivi] also applies, 'Ritrascrivi' too (AC-625 (b): the row's own
 * button then folds into the menu).
 *
 * The import flow is [FlussoImporta]'s, the plain row commands [FlussoComandiRiga]'s (over [ComandiRigaS2]: the
 * date, title and start time, 'Trascrivi'/'Riprova', 'Ritrascrivi' (ADR 0018), 'Annulla' on a queued row), the
 * delete-a-row flow [FlussoEliminaRiga]'s and the Incontro commands [FlussoIncontro]'s (presenter-split): all write
 * through the one [StatoS2] over this presenter's state, which also owns the shared row guard and the refresh after a
 * success; [azioni] binds their functions directly.
 *
 * ADR 0030 §1 (U1): every collaborator above is MANDATORY — the single composition (`:avvio`) always
 * wires all of them, so a missed wiring fails to compile instead of silently hiding a row's control.
 */
@Suppress("LongParameterList") // the screen's context, the list's reads, the player, and one source per flow
class RegistrazioniPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    progettoId: ProgettoId,
    letture: LettureS2,
    aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
    private val apriRegistrazione: (RegistrazioneId) -> Unit,
    eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
    avviaElaborazioniDellIncontro: (AvviaElaborazioniDellIncontro) -> Esito<Unit>,
    comandiRiga: ComandiRigaS2,
) {
    private val _stato = MutableStateFlow<RegistrazioniUiStato>(RegistrazioniUiStato.Caricamento)
    val stato: StateFlow<RegistrazioniUiStato> = _stato.asStateFlow()

    // M1: bumped at the START of every `carica()`; an ERROR is only ever signalled if this is still the
    // latest call when it resolves — a stale failure is dropped rather than shown over a newer attempt
    // that is still pending (whatever that one turns out to do). Mutated only from `scope`'s own
    // (confined) dispatcher.
    private var generazioneCaricamento = 0

    // L485b: the generation of the last result ACTUALLY APPLIED to `_stato` (success only). A SUCCESS is
    // applied whenever it is newer than this — not only when it is the absolute latest call STARTED —
    // so an older call's success is no longer hidden just because a NEWER one already started and then
    // FAILED (which never produced a result of its own to prefer).
    private var generazioneApplicata = 0

    // Before `init`: its collectors may run at once (an immediate dispatcher) and already touch the shared state.
    private val s2 = StatoS2(_stato, scope, io, ::carica)
    private val importazione = FlussoImporta(s2, scope, io, progettoId, aggiungiRegistrazione)
    private val eliminazione = FlussoEliminaRiga(s2, io, lettore, eliminaRegistrazione)
    private val incontro = FlussoIncontro(s2, scope, io, avviaElaborazioniDellIncontro)
    private val comandiDiRiga = FlussoComandiRiga(s2, comandiRiga)
    private val lettura = LetturaS2(letture, lettore::disponibile)

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    /** A failed read is logged (CR-7) and keeps the previous good rows, if any, on screen (H1): never stuck loading. */
    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        val lista = nonFataleRegistrato("carica le registrazioni") { withContext(io) { lettura.lista() } }
        when {
            lista == null -> if (generazione == generazioneCaricamento) segnalaErroreDiCaricamento()
            generazione > generazioneApplicata -> { // L485b
                generazioneApplicata = generazione
                aggiornaConNuoveRighe(lista)
            }
        }
    }

    /**
     * M1: merges freshly-read [nuove] rows into the CURRENT [RegistrazioniUiStato.Dati] instead of
     * rebuilding it — [RegistrazioniUiStato.Dati.importoInCorso]/`errore` and each row's
     * `operazioneInCorso`/`erroreRiga` survive; only their OWNING action ([FlussoImporta]/[StatoS2.comandoDiRiga])
     * ever clears them. L478a: [riproduzione] is RE-DERIVED from [lettore]'s CURRENT (merge-time) state —
     * never decided by [LetturaS2], which only knows whether the player has a row's audio; a play/pause landing on
     * [lettore] while a refresh was still building rows on [io] is never reverted by this merge once it lands.
     */
    private fun aggiornaConNuoveRighe(lista: ListaS2) {
        val nuove = lista.righe
        val precedente = _stato.value as? RegistrazioniUiStato.Dati
        val fuse = nuove.map { nuova ->
            val vecchia = precedente?.righe?.find { it.registrazioneId == nuova.registrazioneId }
            if (vecchia == null) {
                nuova
            } else {
                // AC-376/AC-449: the user's text/open confirmation survive a refresh of the SAME
                // elaborazione state; a state change (e.g. a re-run just got queued) takes the fresh
                // prefill and closes a stale confirmation (nothing to confirm on a row that moved on).
                val stessoStato = vecchia.elaborazione == nuova.elaborazione
                nuova.copy(
                    operazioneInCorso = vecchia.operazioneInCorso,
                    erroreRiga = vecchia.erroreRiga,
                    numeroPersone = if (stessoStato) vecchia.numeroPersone else nuova.numeroPersone,
                    confermaRitrascrivi = vecchia.confermaRitrascrivi && stessoStato,
                    // ADR 0020: a state change while the Elimina confirmation is open (e.g. it just got
                    // queued elsewhere) closes it too — `eliminazione` is a pure function of `elaborazione`,
                    // so `stessoStato` covers it exactly like `confermaRitrascrivi` above.
                    confermaElimina = vecchia.confermaElimina && stessoStato,
                )
            }
        }
        _stato.value = RegistrazioniUiStato.Dati(
            righe = rifletteRiproduzione(fuse, lettore.stato.value), // L478a
            importoInCorso = precedente?.importoInCorso ?: false,
            errore = precedente?.errore,
            erroreAggiornamento = null, // L485a: a SUCCESS always clears a previous refresh error
            avviso = precedente?.avviso, // ADR 0020/AC-627: survives an unrelated refresh, H1-style
            titoloAvviso = precedente?.titoloAvviso ?: ETICHETTA_REGISTRAZIONE_ELIMINATA,
            dialogoImporta = precedente?.dialogoImporta, // AC-I70: an open dialog survives a refresh
            incontri = fondiIncontri(lista.incontri, precedente?.incontri.orEmpty(), incontro::espanso),
        )
    }

    /**
     * M5: the INITIAL load (no [RegistrazioniUiStato.Dati] known yet) fails into a distinct
     * [RegistrazioniUiStato.Errore] with a retry action — never the misleading AC-199 empty-list
     * message. A later refresh failure (already showing [RegistrazioniUiStato.Dati]) keeps the known
     * rows and every in-flight flag on screen (H1), only [RegistrazioniUiStato.Dati.erroreAggiornamento]
     * changes (L485a: never [RegistrazioniUiStato.Dati.errore] — that one is import's own, and an
     * unrelated refresh failure must not touch it either, symmetrically with M1).
     */
    private fun segnalaErroreDiCaricamento() {
        when (val attuale = _stato.value) {
            is RegistrazioniUiStato.Dati -> _stato.value = attuale.copy(erroreAggiornamento = MESSAGGIO_ERRORE_GENERICO)
            RegistrazioniUiStato.Caricamento, is RegistrazioniUiStato.Errore ->
                _stato.value = RegistrazioniUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO)
        }
    }

    /** M5: retries the initial load after [RegistrazioniUiStato.Errore]. L485e: shows
     * [RegistrazioniUiStato.Caricamento] right away — only ever called from [RegistrazioniUiStato.Errore]
     * (the `riprova`/'Riprova' button of that screen alone), so this never wipes a known [Dati] list. */
    fun riprova() {
        _stato.value = RegistrazioniUiStato.Caricamento
        scope.launch { carica() }
    }

    // AC-343: reflects the shared player's live state without re-querying `disponibile` (checked once
    // per `carica`, sticky here — a row already found unavailable stays disabled between refreshes).
    private fun rifletti(s: StatoLettore) =
        s2.aggiornaDati { dati -> dati.copy(righe = rifletteRiproduzione(dati.righe, s)) }

    /**
     * AC-343: always `daMs = 0` — 'restart from the beginning', never a resume from `posizioneMs`.
     * H2: a throwing [LettoreAudio] is logged (CR-7) and mapped to an inline row error, never left to kill this
     * coroutine — [scope] has no supervisor, so an uncaught exception here would also take down the
     * `stato`/`AggiornamentiVista`/`LettoreAudio.stato` collectors launched in `init`.
     */
    fun riproduci(id: RegistrazioneId) {
        scope.launch {
            nonFataleRegistrato("riproduci $id") { withContext(io) { lettore.riproduciDa(id, 0) } }
                ?: s2.aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
        }
    }

    /** AC-343: no target — pauses whichever row `LettoreAudio.stato` currently plays. H2: same
     * exception safety as [riproduci]; the active row (if any) is captured before the call so a
     * failure can still land on it. */
    fun pausa() {
        val idAttivo = lettore.stato.value.registrazioneId
        scope.launch {
            nonFataleRegistrato("pausa") { withContext(io) { lettore.pausa() } }
                ?: idAttivo?.let { id -> s2.aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) } }
        }
    }

    /** AC-203/AC-342/AC-450/AC-451 (ADR 0018): a row opens S3 iff a Trascritto exists — replacing
     * "iff COMPLETATA" — so a row mid re-run (`InAttesa`/`InCorso` with `ritrascrizione`) opens too, on
     * the still-current old transcript; a click on a row without one is a no-op. */
    fun apriRiga(id: RegistrazioneId) {
        val riga = s2.dati?.righe?.find { it.registrazioneId == id } ?: return
        if (riga.trascrittoDisponibile) apriRegistrazione(id)
    }

    /** H1: dismisses the current list-level error, if any — import ([RegistrazioniUiStato.Dati.errore])
     * or refresh ([RegistrazioniUiStato.Dati.erroreAggiornamento], L485a) — whichever [SchermataRegistrazioni]
     * is showing (AC-566: at most one banner on screen). */
    fun chiudiErrore() = s2.aggiornaDati { it.copy(errore = null, erroreAggiornamento = null) }

    /** H1: dismisses the current `erroreRiga` of [id], if any. */
    fun chiudiErroreRiga(id: RegistrazioneId) = s2.aggiornaRiga(id) { it.copy(erroreRiga = null) }

    val azioni: AzioniRegistrazioni = AzioniRegistrazioni(
        importa = importazione::importa,
        modificaData = comandiDiRiga::modificaData,
        rinomina = comandiDiRiga::rinomina,
        riproduci = ::riproduci,
        pausa = ::pausa,
        avviaElaborazione = comandiDiRiga::avviaElaborazione,
        modificaNumeroPersone = comandiDiRiga::modificaNumeroPersone,
        apriRiga = ::apriRiga,
        chiudiErrore = ::chiudiErrore,
        chiudiErroreRiga = ::chiudiErroreRiga,
        riprova = ::riprova,
        ritrascrivi = comandiDiRiga::ritrascrivi,
        annullaRitrascrivi = comandiDiRiga::annullaRitrascrivi,
        confermaRitrascrivi = comandiDiRiga::confermaRitrascrivi,
        annullaElaborazione = comandiDiRiga::annullaElaborazione,
        elimina = eliminazione::chiedi,
        annullaElimina = eliminazione::annulla,
        confermaElimina = eliminazione::conferma,
        chiudiAvviso = s2::azzeraAvviso,
        aggiungiParti = importazione::aggiungiParti,
        scegliImporta = importazione::scegli,
        confermaImporta = importazione::conferma,
        annullaImporta = importazione::annulla,
        espandiIncontro = incontro::espandiIncontro,
        modificaOraDiInizio = comandiDiRiga::modificaOraDiInizio,
        modificaNumeroPersoneIncontro = incontro::modificaNumeroPersoneIncontro,
        avviaElaborazioniIncontro = incontro::avviaElaborazioniIncontro,
        chiudiErroreIncontro = incontro::chiudiErroreIncontro,
    )
}

/** Shared by `rifletti` and `aggiornaConNuoveRighe` (L478a): [righe] with every row's `riproduzione` re-derived from
 * the CURRENT [s] — never a value baked into [righe] from an earlier snapshot; a row whose audio the player cannot play
 * ([StatoRiproduzioneRiga.NonDisponibile], from [LetturaS2]) stays so. */
private fun rifletteRiproduzione(righe: List<RigaRegistrazione>, s: StatoLettore): List<RigaRegistrazione> =
    righe.map { riga ->
        when {
            riga.riproduzione == StatoRiproduzioneRiga.NonDisponibile -> riga
            s.registrazioneId == riga.registrazioneId && s.inRiproduzione ->
                riga.copy(riproduzione = StatoRiproduzioneRiga.InRiproduzione)
            else -> riga.copy(riproduzione = StatoRiproduzioneRiga.Disponibile)
        }
    }

/** AC-I69: an Incontro's own flags survive a refresh like a row's (M1); its field takes the fresh prefill only when its
 * aggregated state moved on; [espanso] is the chevron's single owner's ([FlussoIncontro]). */
private fun fondiIncontri(
    nuovi: List<RigaIncontro>,
    vecchi: List<RigaIncontro>,
    espanso: (IncontroId) -> Boolean,
): List<RigaIncontro> = nuovi.map { nuovo ->
    val vecchio = vecchi.find { it.incontroId == nuovo.incontroId }
    val base = nuovo.copy(espanso = espanso(nuovo.incontroId))
    if (vecchio == null) {
        base
    } else {
        base.copy(
            operazioneInCorso = vecchio.operazioneInCorso,
            errore = vecchio.errore,
            numeroPersone = if (vecchio.stato == nuovo.stato) vecchio.numeroPersone else nuovo.numeroPersone,
        )
    }
}
