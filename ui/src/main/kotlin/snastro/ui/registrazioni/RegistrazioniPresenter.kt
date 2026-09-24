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
import snastro.parlanti.applicazione.letture.ConteggioIdentificazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.etichetta
import snastro.ui.testi.messaggioPer
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * State holder of S2 · Registrazioni del Progetto (RC-2, thin UI): joins `registrazioni-del-progetto`
 * with `stati-elaborazione` by [RegistrazioneId] (R1, AC-342 — [statiElaborazione]/[avviaElaborazione]
 * are `null` in R0: no status column, no 'Trascrivi'/'Riprova', row click does nothing) and with
 * `identificazione-registrazioni` (R2, AC-204/AC-345 — [identificazioni] is `null` in R0/R1: no
 * badge; when supplied, a row's [RigaRegistrazione.identificazione] is built only once BOTH
 * `numVoci` (from `stati-elaborazione`, only ever known for `COMPLETATA`) and the Parlanti count are
 * known for that row — a missing entry (no Trascritto yet) or a failed read of the source leaves
 * just that badge absent, never a provisional or '0' count, without affecting the rest of the row),
 * keeps a
 * per-row reflection of the shared [LettoreAudio] (AC-343: [LettoreAudio.disponibile] is checked once
 * per refresh, [LettoreAudio.stato] is collected live so the play/pause control never goes stale) and
 * refreshes on [AggiornamentiVista] (R15) or after a successful import/`modificaData`/`rinomina`/
 * `avviaElaborazione` (never after a failure — H1, AC-201/AC-206/AC-363/AC-344: "nulla cambia" beyond
 * the inline message). M1: a refresh MERGES the freshly-read rows into the current [RegistrazioniUiStato]
 * instead of rebuilding it from scratch, so a refresh landing mid-flight of an unrelated in-progress
 * operation never wipes [RegistrazioniUiStato.Dati.importoInCorso]/`errore` or a row's
 * `operazioneInCorso`/`erroreRiga`; a generation counter drops a [carica] result that resolves after a
 * newer one already has (out-of-order completion). M5: the INITIAL load failing (no rows known yet)
 * is a distinct [RegistrazioniUiStato.Errore] with a retry action, never the misleading AC-199 empty
 * message.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([registrazioni]/[aggiungiRegistrazione]/
 * [modificaDataRegistrazione]/[rinominaRegistrazione]/[statiElaborazione]/[avviaElaborazione]) rather
 * than the concrete read-model/service classes: `:avvio` binds the real shape (`RegistrazioniDelProgetto::delProgetto`,
 * `<Comando>Servizio::esegui`). This keeps every one of this presenter's own test doubles a plain
 * lambda over Published-Language DTOs (`RegistrazioneDelProgettoVista`, `StatoRegistrazioneVista`,
 * `Esito`) — `:ui` never needs a `*:dominio` aggregate to build a fixture for them (CR-1(b): a
 * `RegistrazioneRepositoryFinta`/`Registrazione.aggiungi` pair would require importing
 * `snastro.progetto.dominio.Registrazione`, off-limits here even from a test file, since the CR-1
 * Konsist rule scans by package, not by source set).
 *
 * ADR 0018 (R2 only, `ritrascrivi`/`annullaElaborazione` optional collaborators): [ritrascrivi] backs
 * 'Ritrascrivi' on a `Completata` row with a Trascritto (AC-448/449) — a NEW `Elaborazione` over the
 * existing one, the same underlying command as [avviaElaborazione] but a distinct, independently
 * supplied knob (`ElaborazioneGiaCompletata` no longer exists, ADR 0018 §1). [annullaElaborazione]
 * (R1+) backs 'Annulla' on a queued row (AC-475/476), no dialog: nothing is lost. Both default to
 * `null`, so a row shows neither control until `avvio-parlanti`/`avvio-composizione` supplies them —
 * `:avvio`'s own existing calls (named args) keep compiling unchanged.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class RegistrazioniPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    private val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    private val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    private val rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
    private val clock: Clock,
    private val statiElaborazione: ((List<RegistrazioneId>) -> List<StatoRegistrazioneVista>)? = null,
    private val avviaElaborazione: ((AvviaElaborazione) -> Esito<Unit>)? = null,
    private val apriRegistrazione: (RegistrazioneId) -> Unit = {},
    private val identificazioni: ((List<RegistrazioneId>) -> List<ConteggioIdentificazione>)? = null,
    private val ritrascrivi: ((AvviaElaborazione) -> Esito<Unit>)? = null,
    private val annullaElaborazione: ((AnnullaElaborazione) -> Esito<Unit>)? = null,
) {
    private val io: CoroutineDispatcher = io

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

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        try {
            val righe = withContext(io) { costruisciRighe() }
            if (generazione > generazioneApplicata) { // L485b
                generazioneApplicata = generazione
                aggiornaConNuoveRighe(righe)
            }
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
     * clears them. L478a: [riproduzione] is RE-DERIVED from [lettore]'s CURRENT (merge-time) state —
     * never trusted from [nuove], which [costruisciRighe] built from a snapshot taken back when this
     * refresh started on [io]; a play/pause landing on [lettore] while that refresh was still building
     * rows would otherwise be silently reverted by this merge once it lands.
     */
    private fun aggiornaConNuoveRighe(nuove: List<RigaRegistrazione>) {
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
                )
            }
        }
        _stato.value = RegistrazioniUiStato.Dati(
            righe = rifletteRiproduzione(fuse, lettore.stato.value), // L478a
            importoInCorso = precedente?.importoInCorso ?: false,
            errore = precedente?.errore,
            erroreAggiornamento = null, // L485a: a SUCCESS always clears a previous refresh error
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

    private fun costruisciRighe(): List<RigaRegistrazione> {
        val progetto = registrazioni()
        val ids = progetto.map { it.registrazioneId }
        val stati = statiElaborazione?.invoke(ids)?.associateBy { it.registrazioneId }
        val conteggiIdentificazione = conteggiIdentificazione(ids)
        val statoLettore = lettore.stato.value
        return progetto.map { r ->
            val vista = stati?.get(r.registrazioneId)
            val elaborazioneRiga = vista?.let(::elaborazioneDi)
            // AC-451: FALLITA over an existing Trascritto renders as Completata + this notice, whatever
            // the `ritrascrivi` source's presence — it reports a FACT, independent of the action's
            // availability (which `ritrascriviDisponibile` gates on its own).
            val ritrascrizioneFallita = vista
                ?.takeIf { it.stato == StatoElaborazioneVista.FALLITA && it.trascrittoDisponibile }
                ?.motivoFallimento
            RigaRegistrazione(
                registrazioneId = r.registrazioneId,
                titolo = r.titolo,
                dataRegistrazione = r.dataRegistrazione,
                durataMs = r.durataMs,
                elaborazione = elaborazioneRiga,
                riproduzione = riproduzioneDi(
                    r.registrazioneId,
                    statoLettore,
                    disponibile = lettore.disponibile(r.registrazioneId),
                ),
                numeroPersone = numeroPersonePrefillDi(vista),
                identificazione = identificazioneDi(vista, conteggiIdentificazione[r.registrazioneId]),
                trascrittoDisponibile = vista?.trascrittoDisponibile == true,
                elaborazioneId = vista?.elaborazioneId,
                ritrascriviDisponibile = elaborazioneRiga == StatoElaborazioneRiga.Completata && ritrascrivi != null,
                ritrascrizioneFallita = ritrascrizioneFallita,
                annullabile = elaborazioneRiga is StatoElaborazioneRiga.InAttesa && annullaElaborazione != null,
            )
        }
    }

    /** AC-376 (FALLITA, unconditional) / AC-448 (Completata, only with the `ritrascrivi` source): the
     * 'Numero di persone' field prefilled from the row's latest Elaborazione — empty otherwise. */
    private fun numeroPersonePrefillDi(v: StatoRegistrazioneVista?): String {
        val fallita = v?.stato == StatoElaborazioneVista.FALLITA
        val completataConRitrascrivi = v?.stato == StatoElaborazioneVista.COMPLETATA && ritrascrivi != null
        return if (fallita || completataConRitrascrivi) v?.numeroPersone?.toString().orEmpty() else ""
    }

    /**
     * AC-345: `null` — never a partial batch — when [identificazioni] is absent (R0/R1) or its read
     * throws; a throw here is contained to this batch (never the outer [carica] catch of
     * [costruisciRighe]'s OTHER sources), so a failing Parlanti source only costs every row its
     * badge, the rest of each row (title, date, status, playback…) stays built from its own source.
     */
    private fun conteggiIdentificazione(ids: List<RegistrazioneId>): Map<RegistrazioneId, ConteggioIdentificazione> =
        try {
            identificazioni?.invoke(ids)?.associateBy { it.registrazioneId }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            emptyMap()
        }

    /** AC-204/AC-345: a badge only once BOTH `numVoci` (only known for `COMPLETATA`) and the
     * Parlanti [conteggio] for this row are known — a missing [vista]/`numVoci`/[conteggio] (source
     * absent, not yet loaded for this row, or failed) means no badge, never a provisional one. */
    private fun identificazioneDi(
        vista: StatoRegistrazioneVista?,
        conteggio: ConteggioIdentificazione?,
    ): IdentificazioneRiga? =
        vista?.numVoci?.let { numVoci -> conteggio?.let { IdentificazioneRiga(numVoci, it.numVociDaIdentificare) } }

    // ADR 0018/AC-450: IN_ATTESA/IN_CORSO carry `ritrascrizione` = trascrittoDisponibile — a re-run
    // over an existing Trascritto gets the "Ritrascrizione …" label instead of the plain one.
    // AC-451: FALLITA over an existing Trascritto is NOT `Fallita` — it renders as `Completata`
    // (`RigaRegistrazione.ritrascrizioneFallita` carries the notice); a plain FALLITA keeps 'Riprova'.
    private fun elaborazioneDi(v: StatoRegistrazioneVista): StatoElaborazioneRiga = when (v.stato) {
        StatoElaborazioneVista.NON_AVVIATA -> StatoElaborazioneRiga.NonAvviata
        StatoElaborazioneVista.IN_ATTESA ->
            StatoElaborazioneRiga.InAttesa(v.posizioneInCoda ?: 0, ritrascrizione = v.trascrittoDisponibile)
        StatoElaborazioneVista.IN_CORSO -> StatoElaborazioneRiga.InCorso(
            faseEtichetta = v.fase?.let(::etichetta).orEmpty(),
            trascorsoMs = v.avviataAlle
                ?.let { Duration.between(it, clock.instant()).toMillis().coerceAtLeast(0) }
                ?: 0,
            ritrascrizione = v.trascrittoDisponibile,
        )
        StatoElaborazioneVista.FALLITA -> if (v.trascrittoDisponibile) {
            StatoElaborazioneRiga.Completata
        } else {
            StatoElaborazioneRiga.Fallita(v.motivoFallimento.orEmpty(), v.elaborazioneId) // L548c
        }
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
    private fun rifletti(s: StatoLettore) =
        aggiornaDati { dati -> dati.copy(righe = rifletteRiproduzione(dati.righe, s)) }

    /** Shared by [rifletti] and [aggiornaConNuoveRighe] (L478a): [righe] with every row's `riproduzione`
     * re-derived from the CURRENT [s] — never a value baked into [righe] from an earlier snapshot. */
    private fun rifletteRiproduzione(righe: List<RigaRegistrazione>, s: StatoLettore): List<RigaRegistrazione> =
        righe.map { riga ->
            if (riga.riproduzione == StatoRiproduzioneRiga.NonDisponibile) {
                riga
            } else {
                riga.copy(riproduzione = riproduzioneDi(riga.registrazioneId, s, disponibile = true))
            }
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

    /**
     * AC-363: renames the Registrazione from the row's inline titolo field — same row guard (M3) and
     * inline error as [modificaData]; the list refreshes only after a success.
     */
    fun rinomina(id: RegistrazioneId, nuovoTitolo: String) =
        suRiga(id) { withContext(io) { rinominaRegistrazione(RinominaRegistrazione(id, nuovoTitolo)) } }

    /**
     * AC-344/AC-203: 'Trascrivi' (NON_AVVIATA) and 'Riprova' (FALLITA) both land here, carrying the row's
     * 'Numero di persone' field (AC-375): empty → `null` (automatic), an integer 1..10 → that number, anything
     * else → the inline [MESSAGGIO_NUMERO_PERSONE_NON_VALIDO] and NO command.
     */
    fun avviaElaborazione(id: RegistrazioneId) {
        val comando = avviaElaborazione ?: return // R0/R1 without the source: the button isn't rendered either
        val riga = rigaLibera(id) ?: return
        when (val campo = numeroPersoneCampo(riga.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido ->
                suRiga(id) { withContext(io) { comando(AvviaElaborazione(id, campo.numero)) } }
        }
    }

    /**
     * AC-449: 'Ritrascrivi' validates the field exactly like [avviaElaborazione] (invalid → the same
     * inline message, no dialog); a valid field opens the inline confirmation instead of sending the
     * command right away.
     */
    fun ritrascrivi(id: RegistrazioneId) {
        if (ritrascrivi == null) return // R0/R1 without the source: neither the field nor the button render
        val riga = rigaLibera(id) ?: return
        when (numeroPersoneCampo(riga.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido ->
                aggiornaRiga(id) { it.copy(confermaRitrascrivi = true, erroreRiga = null) }
        }
    }

    /** AC-449: 'Annulla' on the confirmation — no command is sent, the field keeps its value. */
    fun annullaRitrascrivi(id: RegistrazioneId) = aggiornaRiga(id) { it.copy(confermaRitrascrivi = false) }

    /** AC-449: the confirmed 'Ritrascrivi' — exactly ONE `AvviaElaborazione` with the value [ritrascrivi] validated. */
    fun confermaRitrascrivi(id: RegistrazioneId) {
        val comando = ritrascrivi ?: return
        val riga = rigaLibera(id)?.takeIf { it.confermaRitrascrivi } ?: return
        val numero = (numeroPersoneCampo(riga.numeroPersone) as? NumeroPersoneCampo.Valido)?.numero
        suRiga(id) { withContext(io) { comando(AvviaElaborazione(id, numero)) } }
    }

    /**
     * ADR 0018 Amendment (b) §3: 'Annulla' on an `InAttesa` row (AC-475), no dialog — nothing is lost.
     * AC-476: BOTH outcomes reload the list, unlike every other row action here (H1 "nulla cambia"
     * does not hold for this one): `ElaborazioneGiaAvviata` means the dispatcher's claim raced ahead
     * and the row's real state already changed elsewhere (ADR 0018 Amendment (b) §3's race, now shown
     * inline too — "La trascrizione è già partita…"), and `ElaborazioneNonTrovata` means it is already
     * gone — the reload alone shows the row's accurate current state, with NO inline message (AC-476:
     * "it just reloads" — a message would refer to an Elaborazione the row no longer shows at all).
     */
    fun annullaElaborazione(id: RegistrazioneId) {
        val comando = annullaElaborazione ?: return // R0, or R1/R2 without the source: no 'Annulla' rendered
        val riga = rigaLibera(id) ?: return
        // ReturnCount (detekt): the third guard (a NonAvviata row has no elaborazioneId) is folded into
        // this `?.let` instead of a third `?: return`.
        riga.elaborazioneId?.let { elaborazioneId ->
            aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
            scope.launch {
                try {
                    val esito = withContext(io) { comando(AnnullaElaborazione(elaborazioneId)) }
                    carica() // AC-476: reload on both Ok and Errore (see the kdoc above)
                    val messaggio = (esito as? Esito.Errore)?.errore
                        ?.let { it as? ErroreTrascrizione.ElaborazioneGiaAvviata }
                        ?.let(::messaggioPer)
                    aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = messaggio) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
                }
            }
        }
    }

    /** ADR 0014: the text of [id]'s 'Numero di persone' field, as typed (validated only when an action fires). */
    fun modificaNumeroPersone(id: RegistrazioneId, testo: String) = aggiornaRiga(id) { it.copy(numeroPersone = testo) }

    /** M3: the row [id], unless one of its own operations is already in flight. */
    private fun rigaLibera(id: RegistrazioneId): RigaRegistrazione? {
        val dati = _stato.value as? RegistrazioniUiStato.Dati ?: return null
        return dati.righe.find { it.registrazioneId == id }?.takeUnless { it.operazioneInCorso }
    }

    private fun suRiga(id: RegistrazioneId, operazione: suspend () -> Esito<Unit>) {
        val riga = rigaLibera(id) ?: return
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        scope.launch {
            try {
                when (val esito = operazione()) {
                    is Esito.Ok -> {
                        carica() // M1: merges the refreshed list, preserving this row's flags until reset below
                        aggiornaRiga(id) {
                            it.copy(operazioneInCorso = false, erroreRiga = null, confermaRitrascrivi = false)
                        }
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

    /** AC-203/AC-342/AC-450/AC-451 (ADR 0018): a row opens S3 iff a Trascritto exists — replacing
     * "iff COMPLETATA" — so a row mid re-run (`InAttesa`/`InCorso` with `ritrascrizione`) opens too, on
     * the still-current old transcript; a click elsewhere (or in R0) is a no-op. */
    fun apriRiga(id: RegistrazioneId) {
        val dati = _stato.value as? RegistrazioniUiStato.Dati ?: return
        val riga = dati.righe.find { it.registrazioneId == id } ?: return
        if (riga.trascrittoDisponibile) apriRegistrazione(id)
    }

    /** H1: dismisses the current list-level error, if any — import ([RegistrazioniUiStato.Dati.errore])
     * or refresh ([RegistrazioniUiStato.Dati.erroreAggiornamento], L485a) — whichever [SchermataRegistrazioni]
     * is showing (AC-566: at most one banner on screen). */
    fun chiudiErrore() = aggiornaDati { it.copy(errore = null, erroreAggiornamento = null) }

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
        rinomina = ::rinomina,
        riproduci = ::riproduci,
        pausa = ::pausa,
        avviaElaborazione = ::avviaElaborazione,
        modificaNumeroPersone = ::modificaNumeroPersone,
        apriRiga = ::apriRiga,
        chiudiErrore = ::chiudiErrore,
        chiudiErroreRiga = ::chiudiErroreRiga,
        riprova = ::riprova,
        ritrascrivi = ::ritrascrivi,
        annullaRitrascrivi = ::annullaRitrascrivi,
        confermaRitrascrivi = ::confermaRitrascrivi,
        annullaElaborazione = ::annullaElaborazione,
    )
}

/** ADR 0014/AC-375: the field's text → `null` (empty, automatic), 1..10, or [NonValido]. */
private sealed interface NumeroPersoneCampo {
    data class Valido(val numero: Int?) : NumeroPersoneCampo
    data object NonValido : NumeroPersoneCampo
}

// L548b: a strict `^(10|[1-9])$` match — never `String.toIntOrNull()` on the trimmed text, which
// also accepts a leading sign ("+4".toIntOrNull() == 4) and leading zeros ("04".toIntOrNull() == 4),
// neither a sane representation of a person count.
private val REGEX_NUMERO_PERSONE = Regex("^($NUMERO_PERSONE_MAX|[1-9])$")

private fun numeroPersoneCampo(testo: String): NumeroPersoneCampo {
    val t = testo.trim()
    val numero = t.toIntOrNull()?.takeIf {
        REGEX_NUMERO_PERSONE.matches(t) && it in NUMERO_PERSONE_MIN..NUMERO_PERSONE_MAX
    }
    return when {
        t.isEmpty() -> NumeroPersoneCampo.Valido(null)
        numero != null -> NumeroPersoneCampo.Valido(numero)
        else -> NumeroPersoneCampo.NonValido
    }
}

/**
 * ADR 0014: the range the field accepts before sending the command (AC-375, 'no command' on anything else).
 * `:ui` cannot see the `NumeroPersone` VO (CR-1); `AvviaElaborazione` re-validates it (`NumeroPersone.di`).
 * L548a: `internal` (not `private`) so this boundary is checkable from elsewhere in `:ui`'s own tests; the
 * cross-module parity with the domain's actual range lives in `:avvio`'s own
 * `NumeroPersoneRegistrazioniParitaTest` (`:ui` cannot import `NumeroPersone` itself to share it directly).
 */
internal const val NUMERO_PERSONE_MIN = 1
internal const val NUMERO_PERSONE_MAX = 10
