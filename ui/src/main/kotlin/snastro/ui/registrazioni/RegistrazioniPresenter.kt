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
import snastro.parlanti.applicazione.letture.IdentificazioneIncontro
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.ETICHETTA_REGISTRAZIONE_ELIMINATA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.etichetta
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * State holder of S2 · Registrazioni del Progetto (RC-2, thin UI): joins `registrazioni-del-progetto`
 * with `stati-elaborazione` by [RegistrazioneId] (AC-342) and with `identificazione-registrazioni`
 * (AC-204/AC-345 — a row's [RigaRegistrazione.identificazione] is built only once BOTH `numVoci` (from
 * `stati-elaborazione`, only ever known for `COMPLETATA`) and the Parlanti count are known for that row
 * — a missing entry (no Trascritto yet) or a failed read of the source leaves just that badge absent,
 * never a provisional or '0' count, without affecting the rest of the row), keeps a
 * per-row reflection of the shared [LettoreAudio] (AC-343: [LettoreAudio.disponibile] is checked once
 * per refresh, [LettoreAudio.stato] is collected live so the play/pause control never goes stale) and
 * refreshes on [AggiornamentiVista] (R15) or after a successful import or row command (never after a
 * failure — H1, AC-201/AC-206/AC-363/AC-344: "nulla cambia" beyond the inline message). M1: a refresh MERGES the freshly-read rows into the current [RegistrazioniUiStato]
 * instead of rebuilding it from scratch, so a refresh landing mid-flight of an unrelated in-progress
 * operation never wipes [RegistrazioniUiStato.Dati.importoInCorso]/`errore` or a row's
 * `operazioneInCorso`/`erroreRiga`; a generation counter drops a [carica] result that resolves after a
 * newer one already has (out-of-order completion). M5: the INITIAL load failing (no rows known yet)
 * is a distinct [RegistrazioniUiStato.Errore] with a retry action, never the misleading AC-199 empty
 * message.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([registrazioni]/[aggiungiRegistrazione]/[statiElaborazione]
 * and the row commands' [ComandiRigaS2]) rather than the concrete read-model/service classes: `:avvio` binds the real
 * shape (`RegistrazioniDelProgetto::delProgetto`, `<Comando>Servizio::esegui`). This keeps every one of this
 * presenter's own test doubles a plain lambda over Published-Language DTOs (`RegistrazioneDelProgettoVista`,
 * `StatoRegistrazioneVista`, `Esito`) — `:ui` never needs a `*:dominio` aggregate to build a fixture for them (CR-1(b):
 * a `RegistrazioneRepositoryFinta`/`Registrazione.aggiungi` pair would require importing
 * `snastro.progetto.dominio.Registrazione`, off-limits here even from a test file, since the CR-1 Konsist rule scans by
 * package, not by source set).
 *
 * ADR 0020 §6 ([eliminaRegistrazione]): backs the row's More menu — 'Elimina…' (AC-625/626/627/628) and,
 * on every row where [ComandiRigaS2.ritrascrivi] also applies, 'Ritrascrivi' too (AC-625 (b): the row's own
 * button then folds into the menu).
 *
 * ADR 0023 §4 (block `avvio-coda-condivisa`, [posizioniNellaCoda]): the "In coda (n)"/"Ritrascrizione
 * in coda (n)" position on an `IN_ATTESA` row no longer comes from `StatoRegistrazioneVista` — it is
 * read from `:ui`'s `PosizioniNellaCoda` (the shared queue's owner, `:avvio`) and joined by
 * [RegistrazioneId], one snapshot per [costruisciLista] call, absent meaning no position (0).
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
@Suppress("LongParameterList", "TooManyFunctions") // a source or flow per parameter (to refactor 35); list read+merge
class RegistrazioniPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    progettoId: ProgettoId,
    private val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    private val incontri: () -> List<IncontroDelProgettoVista>,
    aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
    private val clock: Clock,
    private val statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    private val apriRegistrazione: (RegistrazioneId) -> Unit,
    private val identificazioniIncontri: (List<IncontroId>) -> Map<IncontroId, IdentificazioneIncontro>,
    eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
    private val posizioniNellaCoda: () -> PosizioniCoda,
    avviaElaborazioniDellIncontro: (AvviaElaborazioniDellIncontro) -> Esito<Unit>,
    comandiRiga: ComandiRigaS2,
    private val numeroPersonePrecompilato: (IncontroId) -> Int?,
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

    // Before `init`: its collectors may run at once (an immediate dispatcher) and already touch the shared state.
    private val s2 = StatoS2(_stato, scope, io, ::carica)
    private val importazione = FlussoImporta(s2, scope, io, progettoId, aggiungiRegistrazione)
    private val eliminazione = FlussoEliminaRiga(s2, io, lettore, eliminaRegistrazione)
    private val incontro = FlussoIncontro(s2, scope, io, avviaElaborazioniDellIncontro)
    private val comandiDiRiga = FlussoComandiRiga(s2, comandiRiga)

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    /** A failed read is logged (CR-7) and keeps the previous good rows, if any, on screen (H1): never stuck loading. */
    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        val lista = nonFataleRegistrato("carica le registrazioni") { withContext(io) { costruisciLista() } }
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
     * never trusted from [nuove], which [costruisciLista] built from a snapshot taken back when this
     * refresh started on [io]; a play/pause landing on [lettore] while that refresh was still building
     * rows would otherwise be silently reverted by this merge once it lands.
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
            incontri = fondiIncontri(lista.incontri, precedente?.incontri.orEmpty()),
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

    /** AC-I69: an Incontro's own flags survive a refresh like a row's (M1); its field takes the fresh prefill only
     * when its aggregated state moved on; [FlussoIncontro] is the chevron's single owner. */
    private fun fondiIncontri(nuovi: List<RigaIncontro>, vecchi: List<RigaIncontro>): List<RigaIncontro> =
        nuovi.map { nuovo ->
            val vecchio = vecchi.find { it.incontroId == nuovo.incontroId }
            val base = nuovo.copy(espanso = incontro.espanso(nuovo.incontroId))
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

    /** What S2 shows: the Incontri ([incontri]) and the flat Parte rows ([righe]) they point to. */
    private class ListaS2(val righe: List<RigaRegistrazione>, val incontri: List<RigaIncontro>)

    /** One Incontro of the list before its rows exist: [parti] are the Registrazioni present in the catalogue. */
    private class Gruppo(
        val incontroId: IncontroId,
        val titolo: String,
        val data: LocalDate,
        val parti: List<Pair<RegistrazioneDelProgettoVista, LocalTime?>>,
        val noto: Boolean = true,
    )

    /**
     * AC-I69: the Incontri come from [incontri]; a failure of that read (or a Registrazione it does not list) must not
     * fail the whole load — each such Registrazione is then an Incontro of its own, today's S2. Its id is only the
     * Registrazione's (the migration's rule, wrong for a later UUID Incontro, L203): such a group is not [Gruppo.noto],
     * so it offers no 'Aggiungi parti…' that would target an Incontro that may not exist.
     */
    private fun gruppi(progetto: List<RegistrazioneDelProgettoVista>): List<Gruppo> {
        val perId = progetto.associateBy { it.registrazioneId }
        val letti = nonFataleRegistrato("leggi gli incontri") { incontri() }.orEmpty()
        val raggruppati = letti.mapNotNull { i ->
            val parti = i.parti.mapNotNull { p -> perId[p.registrazioneId]?.let { it to p.oraDiInizio } }
            parti.takeIf { it.isNotEmpty() }?.let { Gruppo(i.incontroId, i.titolo, i.data, it) }
        }
        val coperti = raggruppati.flatMap { g -> g.parti.map { it.first.registrazioneId } }.toSet()
        val soli = progetto.filter { it.registrazioneId !in coperti }.map {
            val id = IncontroId(it.registrazioneId.valore)
            Gruppo(id, it.titolo, it.dataRegistrazione, listOf(it to null), noto = false)
        }
        return raggruppati + soli
    }

    private fun costruisciLista(): ListaS2 {
        val gruppi = gruppi(registrazioni())
        val ids = gruppi.flatMap { g -> g.parti.map { it.first.registrazioneId } }
        val stati = statiElaborazione(ids).associateBy { it.registrazioneId }
        val incontriIds = gruppi.map { it.incontroId }
        val conteggi = conteggiIdentificazione(incontriIds)
        // ADR 0023 §4 (sweep, block avvio-coda-condivisa): the queue position is no longer part of
        // StatoRegistrazioneVista — it is read from PosizioniNellaCoda (the shared queue's owner) and
        // joined here by registrazioneId, one snapshot shared by every row (mirrors AC-163's old intent).
        val posizioni = posizioniNellaCoda()
        val statoLettore = lettore.stato.value
        val righe = mutableListOf<RigaRegistrazione>()
        val incontriRighe = gruppi.map { g ->
            val multi = g.parti.size > 1
            val daGruppo = g.parti.mapIndexed { i, (r, ora) ->
                costruisciRiga(
                    r,
                    stati[r.registrazioneId],
                    posizioni,
                    statoLettore,
                    parte = if (multi) ParteDiIncontro(i + 1, g.titolo) else null,
                    ora = if (multi) ora else null,
                    identificazione = if (multi) null else conteggi[g.incontroId],
                )
            }
            righe += daGruppo
            RigaIncontro(
                incontroId = g.incontroId,
                titolo = g.titolo,
                data = g.data,
                durataMs = g.parti.sumOf { it.first.durataMs },
                parti = daGruppo.map { it.registrazioneId },
                stato = statoAggregato(daGruppo),
                identificazione = conteggi[g.incontroId]?.takeIf { multi }
                    ?.let { IdentificazioneRiga(it.numVoci, it.numVociDaIdentificare) },
                numeroPersone = if (multi) precompilato(g.incontroId) else "",
                aggiungiPartiDisponibile = g.noto,
            )
        }
        return ListaS2(righe, incontriRighe)
    }

    /** AC-I67: the latest number used in the Incontro; a failing read only costs the prefill. */
    private fun precompilato(id: IncontroId): String =
        nonFataleRegistrato("leggi il numero di persone di $id") { numeroPersonePrecompilato(id)?.toString() }.orEmpty()

    @Suppress("LongParameterList") // the row's sources: catalogue entry, status, queue, player, Incontro context
    private fun costruisciRiga(
        r: RegistrazioneDelProgettoVista,
        vista: StatoRegistrazioneVista?,
        posizioni: PosizioniCoda,
        statoLettore: StatoLettore,
        parte: ParteDiIncontro?,
        ora: LocalTime?,
        identificazione: IdentificazioneIncontro?,
    ): RigaRegistrazione {
        val elaborazioneRiga = vista?.let { elaborazioneDi(it, posizioni) }
        // AC-451: FALLITA over an existing Trascritto renders as Completata + this notice, whatever
        // the `ritrascrivi` source's presence — it reports a FACT, independent of the action's
        // availability (which `ritrascriviDisponibile` gates on its own).
        val ritrascrizioneFallita = vista
            ?.takeIf { it.stato == StatoElaborazioneVista.FALLITA && it.trascrittoDisponibile }
            ?.motivoFallimento
        return RigaRegistrazione(
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
            identificazione = identificazioneDi(vista, identificazione),
            trascrittoDisponibile = vista?.trascrittoDisponibile == true,
            elaborazioneId = vista?.elaborazioneId,
            ritrascriviDisponibile = elaborazioneRiga == StatoElaborazioneRiga.Completata,
            ritrascrizioneFallita = ritrascrizioneFallita,
            annullabile = elaborazioneRiga is StatoElaborazioneRiga.InAttesa,
            eliminazione = eliminazioneDi(elaborazioneRiga),
            parte = parte,
            oraDiInizio = ora,
        )
    }

    /** ADR 0020 §6/AC-625: disabled with its caption on an open Elaborazione (IN_ATTESA/IN_CORSO, plain
     * or re-run) — every other state (no Elaborazione yet, FALLITA, Completata) is
     * [StatoEliminazione.Disponibile]. */
    private fun eliminazioneDi(elaborazione: StatoElaborazioneRiga?): StatoEliminazione = when {
        elaborazione is StatoElaborazioneRiga.InAttesa ->
            StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA)
        elaborazione is StatoElaborazioneRiga.InCorso ->
            StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO)
        else -> StatoEliminazione.Disponibile
    }

    /** AC-376 (FALLITA) / AC-448 (Completata, `ritrascrivi` is always wired): the 'Numero di persone'
     * field prefilled from the row's latest Elaborazione — empty otherwise. */
    private fun numeroPersonePrefillDi(v: StatoRegistrazioneVista?): String {
        val fallita = v?.stato == StatoElaborazioneVista.FALLITA
        val completataConRitrascrivi = v?.stato == StatoElaborazioneVista.COMPLETATA
        return if (fallita || completataConRitrascrivi) v?.numeroPersone?.toString().orEmpty() else ""
    }

    /**
     * AC-345: an empty map — never a partial batch — when [identificazioniIncontri]'s read throws; a throw here
     * is logged and contained to this batch (never the failure of [carica] over [costruisciLista]'s OTHER sources),
     * so a failing Parlanti source only costs every row its badge, the rest of each row (title, date,
     * status, playback…) stays built from its own source.
     */
    private fun conteggiIdentificazione(ids: List<IncontroId>): Map<IncontroId, IdentificazioneIncontro> =
        nonFataleRegistrato("leggi l'identificazione degli incontri") { identificazioniIncontri(ids) }.orEmpty()

    /** AC-204/AC-345: a badge on a 1-part row only once BOTH `numVoci` (only known for `COMPLETATA`) and the
     * Parlanti [conteggio] of its Incontro are known — a missing [vista]/`numVoci`/[conteggio] (source
     * absent, not yet loaded for this row, or failed) means no badge, never a provisional one. */
    private fun identificazioneDi(
        vista: StatoRegistrazioneVista?,
        conteggio: IdentificazioneIncontro?,
    ): IdentificazioneRiga? =
        vista?.numVoci?.let { numVoci -> conteggio?.let { IdentificazioneRiga(numVoci, it.numVociDaIdentificare) } }

    // ADR 0018/AC-450: IN_ATTESA/IN_CORSO carry `ritrascrizione` = trascrittoDisponibile — a re-run
    // over an existing Trascritto gets the "Ritrascrizione …" label instead of the plain one.
    // AC-451: FALLITA over an existing Trascritto is NOT `Fallita` — it renders as `Completata`
    // (`RigaRegistrazione.ritrascrizioneFallita` carries the notice); a plain FALLITA keeps 'Riprova'.
    private fun elaborazioneDi(
        v: StatoRegistrazioneVista,
        posizioni: PosizioniCoda,
    ): StatoElaborazioneRiga = when (v.stato) {
        StatoElaborazioneVista.NON_AVVIATA -> StatoElaborazioneRiga.NonAvviata
        StatoElaborazioneVista.IN_ATTESA ->
            StatoElaborazioneRiga.InAttesa(
                posizioni.elaborazioni[v.registrazioneId] ?: 0,
                ritrascrizione = v.trascrittoDisponibile,
            )
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
        s2.aggiornaDati { dati -> dati.copy(righe = rifletteRiproduzione(dati.righe, s)) }

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
