package snastro.ui.registrazioni

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.IdentificazioneIncontro
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.dominio.ErroreProgetto
import snastro.supporto.catturaNonFatale
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.comandoConfermato
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.ETICHETTA_PARTI_AGGIUNTE
import snastro.ui.testi.ETICHETTA_REGISTRAZIONE_ELIMINATA
import snastro.ui.testi.MESSAGGIO_ELIMINAZIONE_RIFIUTATA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.etichetta
import snastro.ui.testi.messaggioEliminata
import snastro.ui.testi.messaggioImportTuttoONiente
import snastro.ui.testi.messaggioPartiAggiunte
import snastro.ui.testi.messaggioPer
import java.io.File
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
 * ADR 0018 ([ritrascrivi]/[annullaElaborazione]): [ritrascrivi] backs 'Ritrascrivi' on a `Completata`
 * row with a Trascritto (AC-448/449) — a NEW `Elaborazione` over the existing one, the same underlying
 * command as [avviaElaborazione] but a distinct, independently supplied knob (`ElaborazioneGiaCompletata`
 * no longer exists, ADR 0018 §1). [annullaElaborazione] backs 'Annulla' on a queued row (AC-475/476), no
 * dialog: nothing is lost.
 *
 * ADR 0020 §6 ([eliminaRegistrazione]): backs the row's More menu — 'Elimina…' (AC-625/626/627/628) and,
 * on every row where [ritrascrivi] also applies, 'Ritrascrivi' too (AC-625 (b): the row's own button
 * then folds into the menu).
 *
 * ADR 0023 §4 (block `avvio-coda-condivisa`, [posizioniNellaCoda]): the "In coda (n)"/"Ritrascrizione
 * in coda (n)" position on an `IN_ATTESA` row no longer comes from `StatoRegistrazioneVista` — it is
 * read from `:ui`'s `PosizioniNellaCoda` (the shared queue's owner, `:avvio`) and joined by
 * [RegistrazioneId], one snapshot per [costruisciRighe] call, absent meaning no position (0).
 *
 * ADR 0030 §1 (U1): every collaborator above is MANDATORY — the single composition (`:avvio`) always
 * wires all of them, so a missed wiring fails to compile instead of silently hiding a row's control.
 */
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass") // a parameter per collaborator, a method per action
class RegistrazioniPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val progettoId: ProgettoId,
    private val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    private val incontri: () -> List<IncontroDelProgettoVista>,
    private val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    private val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    private val rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
    private val clock: Clock,
    private val statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    private val avviaElaborazione: (AvviaElaborazione) -> Esito<Unit>,
    private val apriRegistrazione: (RegistrazioneId) -> Unit,
    private val identificazioniIncontri: (List<IncontroId>) -> Map<IncontroId, IdentificazioneIncontro>,
    private val ritrascrivi: (AvviaElaborazione) -> Esito<Unit>,
    private val annullaElaborazione: (AnnullaElaborazione) -> Esito<Unit>,
    private val eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
    private val posizioniNellaCoda: () -> PosizioniCoda,
    private val avviaElaborazioniDellIncontro: (AvviaElaborazioniDellIncontro) -> Esito<Unit>,
    private val modificaOraDiInizioRegistrazione: (RegistrazioneId, LocalTime?) -> Esito<Unit>,
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

    // AC-I69: the expanded Incontri — presenter state, so the chevron survives a round trip to S3 and every refresh.
    private val espansi = mutableSetOf<IncontroId>()

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        try {
            val lista = withContext(io) { costruisciLista() }
            if (generazione > generazioneApplicata) { // L485b
                generazioneApplicata = generazione
                aggiornaConNuoveRighe(lista)
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
     * when its aggregated state moved on; [espansi] is the chevron's single owner. */
    private fun fondiIncontri(nuovi: List<RigaIncontro>, vecchi: List<RigaIncontro>): List<RigaIncontro> =
        nuovi.map { nuovo ->
            val vecchio = vecchi.find { it.incontroId == nuovo.incontroId }
            val base = nuovo.copy(espanso = nuovo.incontroId in espansi)
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
        val letti = catturaNonFatale { incontri() }.getOrDefault(emptyList())
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
        catturaNonFatale { numeroPersonePrecompilato(id)?.toString().orEmpty() }.getOrDefault("")

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
     * is contained to this batch (never the outer [carica] catch of [costruisciLista]'s OTHER sources),
     * so a failing Parlanti source only costs every row its badge, the rest of each row (title, date,
     * status, playback…) stays built from its own source.
     */
    private fun conteggiIdentificazione(ids: List<IncontroId>): Map<IncontroId, IdentificazioneIncontro> =
        try {
            identificazioniIncontri(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            emptyMap()
        }

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
     * AC-199..201/AC-I70: drag-and-drop and the file picker both land here. ONE file imports at once as a new
     * Incontro (`NuovoIncontro`) with its failure reported inline; 2+ files open the import dialog first (D-0019).
     */
    fun importa(percorsi: List<String>) {
        val attuale = _stato.value
        val libero = attuale is RegistrazioniUiStato.Dati && !attuale.importoInCorso && attuale.dialogoImporta == null
        if (percorsi.isEmpty() || !libero) return // M3
        attuale as RegistrazioniUiStato.Dati
        if (percorsi.size > 1) {
            _stato.value = attuale.copy(dialogoImporta = DialogoImporta(percorsi), errore = null, avviso = null)
            return
        }
        // ADR 0020/AC-627: "the next command" clears any stale Elimina notice too.
        _stato.value = attuale.copy(importoInCorso = true, errore = null, avviso = null)
        val percorso = percorsi.single()
        scope.launch {
            try {
                val comando = AggiungiRegistrazione(progettoId, percorsi, Destinazione.NuovoIncontro)
                val errore = inviaImport(comando) { messaggioImportFallito(percorso, messaggioPer(it)) }
                carica() // M1: merges the refreshed list, preserving importoInCorso/errore until reset below
                aggiornaDati { it.copy(importoInCorso = false, errore = errore) }
            } finally {
                aggiornaDati { it.copy(importoInCorso = false) } // L269: an Error propagates, S2 is released
            }
        }
    }

    /** AC-I70: 'Un incontro in N parti' / 'N incontri separati' toggle. */
    fun scegliImporta(scelta: SceltaImporta) =
        aggiornaDialogo { if (it.invioInCorso) it else it.copy(scelta = scelta) }

    /** AC-I70: 'Annulla' closes the dialog; nothing is sent (not while the command is in flight). */
    fun annullaImporta() = aggiornaDati { d ->
        if (d.dialogoImporta?.invioInCorso == true) d else d.copy(dialogoImporta = null)
    }

    /** AC-I70/AC-I71: 'Importa' sends ONE all-or-nothing command (ADR 0033 §2); on failure the dialog stays open. */
    fun confermaImporta() {
        val dialogo = (_stato.value as? RegistrazioniUiStato.Dati)?.dialogoImporta ?: return
        if (dialogo.invioInCorso) return
        val destinazione = when (dialogo.scelta) {
            SceltaImporta.UnIncontro -> Destinazione.NuovoIncontro
            SceltaImporta.IncontriSeparati -> Destinazione.IncontriSeparati
        }
        aggiornaDialogo { it.copy(invioInCorso = true, errore = null) }
        scope.launch {
            try {
                val esito = inviaImport(AggiungiRegistrazione(progettoId, dialogo.percorsi, destinazione))
                if (esito == null) {
                    carica()
                    aggiornaDati { it.copy(dialogoImporta = null) }
                } else {
                    aggiornaDialogo { it.copy(invioInCorso = false, errore = esito) }
                }
            } finally {
                aggiornaDialogo { it.copy(invioInCorso = false) } // L269
            }
        }
    }

    /**
     * AC-I71: "Aggiungi parti…" — [percorsi] come from the row's file picker; ONE command to
     * `Destinazione.Incontro([incontroId])`. Success: the closable notice '2 parti aggiunte a «[titolo]».';
     * failure: [RegistrazioniUiStato.Dati.errore] and nothing changes.
     */
    fun aggiungiParti(incontroId: IncontroId, titolo: String, percorsi: List<String>) {
        if (percorsi.isEmpty()) return
        val attuale = _stato.value
        if (attuale !is RegistrazioniUiStato.Dati || attuale.importoInCorso || attuale.dialogoImporta != null) return
        _stato.value = attuale.copy(importoInCorso = true, errore = null, avviso = null)
        scope.launch {
            try {
                val errore = inviaImport(AggiungiRegistrazione(progettoId, percorsi, Destinazione.Incontro(incontroId)))
                if (errore == null) carica()
                aggiornaDati {
                    it.copy(
                        importoInCorso = false,
                        errore = errore,
                        avviso = if (errore == null) messaggioPartiAggiunte(percorsi.size, titolo) else null,
                        titoloAvviso = ETICHETTA_PARTI_AGGIUNTE,
                    )
                }
            } finally {
                aggiornaDati { it.copy(importoInCorso = false) } // L269
            }
        }
    }

    /** Runs [comando] off the UI thread; `null` on success (a committed import too, D-0062), else the failure text. */
    private suspend fun inviaImport(
        comando: AggiungiRegistrazione,
        messaggio: (ErroreDominio) -> String = ::messaggioImportTuttoONiente,
    ): String? = try {
        when (val esito = withContext(io) { comandoConfermato("import") { aggiungiRegistrazione(comando) } }) {
            is Esito.Ok -> null
            is Esito.Errore -> messaggio(esito.errore)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
        MESSAGGIO_ERRORE_GENERICO
    }

    private fun RegistrazioniUiStato.Dati.conAvvisoEliminata(titolo: String) =
        copy(avviso = messaggioEliminata(titolo), titoloAvviso = ETICHETTA_REGISTRAZIONE_ELIMINATA)

    private fun aggiornaDialogo(f: (DialogoImporta) -> DialogoImporta) =
        aggiornaDati { d -> d.dialogoImporta?.let { d.copy(dialogoImporta = f(it)) } ?: d }

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
        val comando = avviaElaborazione
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
        val comando = ritrascrivi
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
        val comando = annullaElaborazione
        val riga = rigaLibera(id) ?: return
        // ReturnCount (detekt): the third guard (a NonAvviata row has no elaborazioneId) is folded into
        // this `?.let` instead of a third `?: return`.
        riga.elaborazioneId?.let { elaborazioneId ->
            aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
            azzeraAvviso() // ADR 0020/AC-627: "the next command" clears any stale Elimina notice too
            scope.launch {
                try {
                    val esito = withContext(io) {
                        comandoConfermato("annulla $elaborazioneId") { comando(AnnullaElaborazione(elaborazioneId)) }
                    }
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
                } finally {
                    aggiornaRiga(id) { it.copy(operazioneInCorso = false) } // L269
                }
            }
        }
    }

    /**
     * AC-626: 'Elimina…' opens the row's confirmation — a no-op unless [RigaRegistrazione.eliminazione]
     * is [StatoEliminazione.Disponibile] (AC-625: "a disabled item sends nothing", checked here too,
     * not only by the view's own disabled `DropdownMenuItem`).
     */
    fun elimina(id: RegistrazioneId) {
        val riga = rigaLibera(id) ?: return
        // ReturnCount (detekt): the third guard (only StatoEliminazione.Disponibile opens the
        // confirmation) is folded into this `if`, like RegistrazioniPresenter.annullaElaborazione's own.
        if (riga.eliminazione == StatoEliminazione.Disponibile) {
            aggiornaRiga(id) { it.copy(confermaElimina = true, erroreRiga = null) }
        }
    }

    /** AC-626: 'Annulla' on the confirmation — no command is sent, the row is unchanged. */
    fun annullaElimina(id: RegistrazioneId) = aggiornaRiga(id) { it.copy(confermaElimina = false) }

    /**
     * AC-627/628: the confirmed 'Elimina' — exactly ONE `EliminaRegistrazione`. On Ok: pauses the
     * player first if it is currently playing THIS row (AC-627), then reloads (the row disappears) and
     * shows the dismissible success notice with the titolo CAPTURED before the reload (the row itself
     * is gone from the fresh read). On Errore the dialog always closes (AC-628): `RegistrazioneNonTrovata`
     * just reloads (the row is already gone remotely, nothing to show it on); `ElaborazioneGiaAperta`
     * (the race backstop) reloads AND shows [MESSAGGIO_ELIMINAZIONE_RIFIUTATA] inline — a dedicated text,
     * not the generic [messaggioPer] line for the same error type used by `AvviaElaborazione`/`Ritrascrivi`;
     * any other Errore shows the generic [messaggioPer] text (AC-180), no reload (H1: "nulla cambia"
     * beyond the inline message, same as every other row command here).
     */
    fun confermaElimina(id: RegistrazioneId) {
        val comando = eliminaRegistrazione
        val riga = rigaLibera(id)?.takeIf { it.confermaElimina } ?: return
        val titolo = riga.titolo
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        azzeraAvviso()
        scope.launch {
            try {
                val esito = withContext(io) { comandoConfermato("elimina $id") { comando(EliminaRegistrazione(id)) } }
                when (esito) {
                    is Esito.Ok -> {
                        val statoLettore = lettore.stato.value
                        if (statoLettore.registrazioneId == id && statoLettore.inRiproduzione) {
                            withContext(io) { lettore.pausa() }
                        }
                        carica()
                        aggiornaDati { it.conAvvisoEliminata(titolo) }
                    }
                    is Esito.Errore -> {
                        val errore = esito.errore
                        when {
                            // AC-628: "the list just reloads" — the row is normally already gone from the
                            // fresh read too, so this reset is a no-op then; it only matters for the rare
                            // race where a stale read still shows it, so the dialog still closes and the
                            // row is not left stuck spinning (AC-628's own "dialog closed" applies here too).
                            errore is ErroreProgetto.RegistrazioneNonTrovata -> {
                                carica()
                                aggiornaRiga(id) { it.copy(operazioneInCorso = false, confermaElimina = false) }
                            }
                            errore is ErroreTrascrizione.ElaborazioneGiaAperta -> {
                                carica()
                                aggiornaRiga(id) {
                                    it.copy(
                                        operazioneInCorso = false,
                                        confermaElimina = false,
                                        erroreRiga = MESSAGGIO_ELIMINAZIONE_RIFIUTATA,
                                    )
                                }
                            }
                            else -> aggiornaRiga(id) {
                                it.copy(
                                    operazioneInCorso = false,
                                    confermaElimina = false,
                                    erroreRiga = messaggioPer(errore),
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaRiga(id) {
                    it.copy(operazioneInCorso = false, confermaElimina = false, erroreRiga = MESSAGGIO_ERRORE_GENERICO)
                }
            } finally {
                aggiornaRiga(id) { it.copy(operazioneInCorso = false) } // L269
            }
        }
    }

    /** AC-627: dismisses the post-elimination success notice, if any. */
    fun chiudiAvviso() = azzeraAvviso()

    /** AC-I69: the chevron of [id]; the set lives here so it outlives S2's composition and every refresh. */
    fun espandiIncontro(id: IncontroId) {
        if (!espansi.add(id)) espansi.remove(id)
        aggiornaIncontro(id) { it.copy(espanso = id in espansi) }
    }

    /**
     * AC-I68: replaces a Parte's start time ([ora] `null` clears it) — `ModificaOraDiInizio`, whose `OraDiInizio` VO
     * `:avvio` builds (CR-1: `:ui` does not see the domain). The list is read again on success, so the sub-rows come
     * back in their new order; a refusal stays on the Parte's row ([RigaRegistrazione.erroreRiga]).
     */
    fun modificaOraDiInizio(id: RegistrazioneId, ora: LocalTime?) {
        val comando = modificaOraDiInizioRegistrazione
        suRiga(id) { withContext(io) { comando(id, ora) } }
    }

    /** AC-I67: the text of an Incontro's ONE 'Numero di persone' field, as typed (validated when 'Trascrivi' fires). */
    fun modificaNumeroPersoneIncontro(id: IncontroId, testo: String) =
        aggiornaIncontro(id) { it.copy(numeroPersone = testo) }

    /**
     * AC-I67: 'Trascrivi N parti' — the field validated like a row's (invalid → the same inline message, nothing
     * sent), then ONE `AvviaElaborazioniDellIncontro(id, n)` over the Incontro.
     */
    fun avviaElaborazioniIncontro(id: IncontroId) {
        val comando = avviaElaborazioniDellIncontro
        val incontro = incontroLibero(id) ?: return
        when (val campo = numeroPersoneCampo(incontro.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                aggiornaIncontro(id) { it.copy(errore = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido -> {
                aggiornaIncontro(id) { it.copy(operazioneInCorso = true, errore = null) }
                azzeraAvviso()
                scope.launch {
                    val avvia = AvviaElaborazioniDellIncontro(id, campo.numero)
                    val esito = catturaNonFatale {
                        withContext(io) { comandoConfermato("trascrivi $id") { comando(avvia) } }
                    }.getOrNull()
                    val errore = when (esito) {
                        is Esito.Ok -> null
                        is Esito.Errore -> messaggioPer(esito.errore)
                        null -> MESSAGGIO_ERRORE_GENERICO
                    }
                    if (errore == null) carica()
                    aggiornaIncontro(id) { it.copy(operazioneInCorso = false, errore = errore) }
                }
            }
        }
    }

    /** AC-I67: dismisses the inline message of Incontro [id]. */
    fun chiudiErroreIncontro(id: IncontroId) = aggiornaIncontro(id) { it.copy(errore = null) }

    private fun incontroLibero(id: IncontroId): RigaIncontro? =
        (_stato.value as? RegistrazioniUiStato.Dati)?.incontri?.find { it.incontroId == id }
            ?.takeUnless { it.operazioneInCorso }

    private fun aggiornaIncontro(id: IncontroId, f: (RigaIncontro) -> RigaIncontro) =
        aggiornaDati { d -> d.copy(incontri = d.incontri.map { if (it.incontroId == id) f(it) else it }) }

    /** ADR 0014: the text of [id]'s 'Numero di persone' field, as typed (validated only when an action fires). */
    fun modificaNumeroPersone(id: RegistrazioneId, testo: String) = aggiornaRiga(id) { it.copy(numeroPersone = testo) }

    /** M3: the row [id], unless one of its own operations is already in flight. */
    private fun rigaLibera(id: RegistrazioneId): RigaRegistrazione? {
        val dati = _stato.value as? RegistrazioniUiStato.Dati ?: return null
        return dati.righe.find { it.registrazioneId == id }?.takeUnless { it.operazioneInCorso }
    }

    /** One row command; committed with only its after-commit follow-up failed is a success (D-0062, L270). */
    private fun suRiga(id: RegistrazioneId, operazione: suspend () -> Esito<Unit>) {
        val riga = rigaLibera(id) ?: return
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        azzeraAvviso() // ADR 0020/AC-627: "the next command" clears any stale Elimina notice too
        scope.launch {
            try {
                when (val esito = comandoConfermato("comando di riga su $id") { operazione() }) {
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
            } finally {
                // L269 (D-0065): an Error (e.g. after the commit) propagates, but the row is released.
                aggiornaRiga(id) { it.copy(operazioneInCorso = false) }
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
     * the still-current old transcript; a click on a row without one is a no-op. */
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

    /** ADR 0020/AC-627: clears [RegistrazioniUiStato.Dati.avviso] — "fino a chiudiAvviso o al comando
     * successivo" (called by [chiudiAvviso] and by every other command's own entry point). */
    private fun azzeraAvviso() = aggiornaDati { it.copy(avviso = null) }

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
        elimina = ::elimina,
        annullaElimina = ::annullaElimina,
        confermaElimina = ::confermaElimina,
        chiudiAvviso = ::chiudiAvviso,
        aggiungiParti = ::aggiungiParti,
        scegliImporta = ::scegliImporta,
        confermaImporta = ::confermaImporta,
        annullaImporta = ::annullaImporta,
        espandiIncontro = ::espandiIncontro,
        modificaOraDiInizio = ::modificaOraDiInizio,
        modificaNumeroPersoneIncontro = ::modificaNumeroPersoneIncontro,
        avviaElaborazioniIncontro = ::avviaElaborazioniIncontro,
        chiudiErroreIncontro = ::chiudiErroreIncontro,
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
