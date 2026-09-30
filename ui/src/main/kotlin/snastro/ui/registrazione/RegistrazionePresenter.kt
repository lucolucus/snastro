package snastro.ui.registrazione

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.AggiornamentiVista
import snastro.ui.ApriEsterno
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.lettore.StatoLettore
import snastro.ui.stile.SegnoScheda
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO

/**
 * State holder of S3 · Registrazione (RC-2, thin UI; AC-207/208/217/218). Joins `trascritto-view`
 * ([trascritto]) with the Sbobinatura's resolved path ([sbobinatura]) and reflects the shared [lettore]
 * (AC-208: which Segmento is currently playing; AC-217: the header audio bar disabled, with a message,
 * when the source is missing — clicking a Segmento then does nothing, the transcript itself stays
 * readable). [parlanti] ([SorgentiParlanti]) drives the Voci panel, the Nome labels, the selection
 * toolbar and the Revisione commands, all handled by [StatoVoci] into this presenter's own [stato].
 * [SOGLIA_ATTESA_VISIBILE_MS] (ADR 0017 §3) is defined here, once.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([trascritto]/[sbobinatura]) rather than the
 * concrete query classes, mirroring `RegistrazioniPresenter` (dev-architecture `#presenter`): `:avvio`
 * binds the real shape (`TrascrittoQuery::vista` bound to [registrazioneId]; the Sbobinatura's absolute
 * path under `sbobinature/`, joined from `sbobinatura`'s `nomeFile` and the open Progetto's folder) — this
 * presenter's own test doubles stay plain lambdas over Published-Language values, never a `*:dominio`
 * type (CR-1(b)).
 *
 * ADR 0018 (AC-452/453, [stati]/[aggiornamenti]): while the latest Elaborazione of [registrazioneId] is
 * `in_attesa`/`in_corso` (a re-run over the Trascritto shown here), the screen is READ-ONLY —
 * [RegistrazioneUiStato.Dati.soloLettura] + the banner. [aggiornamenti] (R15, `tec-shell-ui`) reloads on
 * the Cambiamento the replacement/cancellation publishes, so the read-only flag, the banner and (on a
 * replacement) the transcript itself stay current (AC-453).
 *
 * ADR 0021 (AC-S119..S123, [riassunto]/[selezioneSchedaS3]): [riassunto] the Riassunto tab content + its
 * status mark, [selezioneSchedaS3] the ONE per-window holder shared by every [RegistrazionePresenter] it
 * builds, so the selected tab survives navigating to another recording (AC-S121).
 *
 * ADR 0030 §1 (U1): every collaborator above is MANDATORY — the single composition (`:avvio`) always
 * wires all of them, so a missed wiring fails to compile instead of silently hiding a screen area.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class RegistrazionePresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val trascritto: () -> TrascrittoView?,
    private val sbobinatura: () -> String?,
    private val lettore: LettoreAudio,
    private val apriEsterno: ApriEsterno,
    private val parlanti: SorgentiParlanti,
    private val stati: () -> StatoRegistrazioneVista?,
    private val aggiornamenti: AggiornamentiVista,
    private val riassunto: SorgenteRiassuntoS3,
    private val selezioneSchedaS3: SelezioneSchedaS3,
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RegistrazioneUiStato>(RegistrazioneUiStato.Caricamento)
    val stato: StateFlow<RegistrazioneUiStato> = _stato.asStateFlow()

    // L573a (rework cycle 1, HIGH): every `lettore` command runs on this ONE lane, never on the full
    // (possibly multi-threaded) `io` — a real port implementation can decode with ffmpeg for seconds
    // (AC-241), so two real calls on plain `io` could run CONCURRENTLY and finish in either order,
    // exactly the race `LettorePresenter`'s own HIGH-1 fix closed for its single shared instance. Here
    // `lettore` is reached directly, so the same single-lane guard is repeated at this call site.
    private val ioLettore: CoroutineDispatcher = io.limitedParallelism(1)

    // The ONE Job of whichever `lettore` command (segmento/estratto/pausa/header-play) is still in
    // flight — a later one always cancels an earlier one that has NOT yet started (still queued for
    // the lane above), so a click that hasn't reached the port at all is dropped outright rather than
    // firing late. Once a command HAS started, the lane alone (not cancellation) is what keeps a later
    // one from running concurrently with it — cancelling it back cannot interrupt a port call already
    // in flight (a plain, non-suspending call has no suspension point to cancel at), but the lane makes
    // the later command simply WAIT its turn instead of racing it.
    private var comandoLettoreInCorso: Job? = null

    // L573b: `true` from a THROWN `lettore.disponibile()` (never from a clean `false`) until a real
    // `lettore.stato` tick for THIS Registrazione arrives (`rifletti`) settles the question. Read by
    // [barraDi] — never part of [RegistrazioneUiStato.Dati] itself, since it is a presenter-private
    // "we don't actually know yet", not a datum the view needs to carry.
    private var disponibilitaIncerta = false

    // The Voci panel, the Nome labels, the selection toolbar and the Revisione commands.
    private val voci: StatoVoci =
        StatoVoci(parlanti, scope, io, registrazioneId, trascritto, _stato) { v -> segmentiDi(v, lettore.stato.value) }

    // AC-S122: the Riassunto tab's own status mark — collected independently of `carica()` (it can tick
    // while a reload is not otherwise due) and kept here so a reload (`carica()`) never loses the latest
    // value: `Dati` is REPLACED whole there, so anything not re-read from a live source would reset to
    // `null` on every reload.
    private var segnoRiassuntoAttuale: SegnoScheda? = null

    // Pre-release finding #130 (rework, LOW): `riassunto`/`registrazioneId` never change over this
    // presenter's lifetime, so ONE lambda instance — never a fresh one per `carica()` — is correctly
    // stable: `Dati.equals()` (and so the slot's own recomposition) used to see a NEW `contenutoRiassunto`
    // on every reload even when nothing about the Riassunto tab itself changed.
    private val contenutoRiassunto: @Composable () -> Unit = { riassunto.contenuto(registrazioneId) }

    init {
        scope.launch { carica() }
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
        // AC-453: reloads on any Cambiamento of this Registrazione — the replacement/cancellation event
        // included (R15, same pattern as RegistrazioniPresenter/ParlantiPresenter).
        scope.launch {
            aggiornamenti.cambiamenti.collect { c ->
                if (c.registrazioneId == null || c.registrazioneId == registrazioneId) carica()
            }
        }
        scope.launch {
            riassunto.segno(registrazioneId).collect { segno ->
                segnoRiassuntoAttuale = segno
                aggiornaDati { it.copy(segnoRiassunto = segno) }
            }
        }
        voci.avvia()
    }

    private suspend fun carica() {
        try {
            // AC-453/455: a reload never keeps a selection or a panel from a previous generation.
            voci.deseleziona()
            val vista = withContext(io) { trascritto() }
            if (vista == null) {
                _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
                return
            }
            // L573b: `sbobinatura()`/`lettore.disponibile()` degrade PER CALL — a fault of either one used
            // to fall into the same catch below as `trascritto()` and take the WHOLE screen to `Errore`,
            // discarding the vista just read. `sbobinatura()` is the simpler of the two: `null` is already
            // its own legitimate "not resolved yet" value (AC-218 disables 'Apri sbobinatura'/'Mostra
            // nella cartella' the same way, no separate signal is worth adding), so a thrown call is
            // folded into that same `null` by [sbobinaturaOSicuro] — never a silent swallow, just no extra
            // state for a fault that already has a safe, defined UI.
            val percorso = sbobinaturaOSicuro()
            // `lettore.disponibile()` is NOT folded the same way: a THROWN call says nothing about the
            // source itself (unlike a clean `false`), so it must not look like "audio non disponibile"
            // (which disables retry). [disponibileOSicuro] tells the two apart; a throw sets
            // [disponibilitaIncerta] instead, [barraDi] maps THAT to the lettore's transient `Errore`
            // state (play stays enabled) and [audioDisponibile] below stays `true` so a retry click is
            // never blocked. `disponibilitaIncerta` clears itself once a real `lettore.stato` tick for
            // THIS Registrazione arrives (`rifletti`) and settles the question.
            val disponibileEsito = disponibileOSicuro()
            disponibilitaIncerta = disponibileEsito == null
            val disponibile = disponibileEsito ?: true
            val statoLettore = lettore.stato.value
            val soloLettura = soloLetturaDi(withContext(io) { stati() })
            voci.vista = vista
            _stato.value = RegistrazioneUiStato.Dati(
                titolo = vista.titolo,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
                segmenti = segmentiDi(vista, statoLettore),
                barra = barraDi(statoLettore, disponibile),
                audioDisponibile = disponibile,
                sbobinaturaPercorso = percorso,
                soloLettura = soloLettura,
                bannerRitrascrizione = if (soloLettura) MESSAGGIO_RITRASCRIZIONE_IN_CORSO else null,
                contenutoRiassunto = contenutoRiassunto,
                schedaSelezionata = selezioneSchedaS3.scheda,
                segnoRiassunto = segnoRiassuntoAttuale,
            )
            voci.pubblica()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Same rationale as every other presenter in this codebase (RegistrazioniPresenter/ShellPresenter):
            // a fault of trascritto/sbobinatura/lettore never leaves this screen stuck on Caricamento.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
        }
        if (_stato.value is RegistrazioneUiStato.Dati) voci.ricaricaParlanti()
    }

    // L573b: `sbobinatura()` degraded to `null` (same as it legitimately having none) — 'Apri sbobinatura'/
    // 'Mostra nella cartella' just stay disabled (AC-218), the transcript is untouched.
    private suspend fun sbobinaturaOSicuro(): String? = try {
        withContext(io) { sbobinatura() }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        null
    }

    // L573b: `null` means the call THREW (told apart from a clean `false`, see `carica()`) — the whole
    // screen never goes to Errore for this, but a thrown call must not read as "confirmed unavailable"
    // either.
    private suspend fun disponibileOSicuro(): Boolean? = try {
        withContext(io) { lettore.disponibile(registrazioneId) }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        null
    }

    /** Retries the initial load after [RegistrazioneUiStato.Errore] — shows [RegistrazioneUiStato.Caricamento]
     * while the retry itself is in flight (L573f), instead of leaving the stale Errore on screen. */
    fun riprova() {
        _stato.value = RegistrazioneUiStato.Caricamento
        scope.launch { carica() }
    }

    /** AC-452: `true` iff the latest Elaborazione is `in_attesa`/`in_corso` — a re-run over the
     * Trascritto shown here; `null` (no [stati] source, or `NON_AVVIATA`/`FALLITA`/`COMPLETATA`) → not
     * read-only. */
    private fun soloLetturaDi(v: StatoRegistrazioneVista?): Boolean =
        v?.stato == StatoElaborazioneVista.IN_ATTESA || v?.stato == StatoElaborazioneVista.IN_CORSO

    private fun segmentiDi(vista: TrascrittoView, statoLettore: StatoLettore): List<SegmentoRiga> {
        return vista.segmenti.map { s ->
            SegmentoRiga(
                segmentoId = s.segmentoId,
                voceId = s.voceId,
                etichettaVoce = etichettaDiVoce(vista, s.voceId, voci.nomeDi(s.voceId)),
                inizioMs = s.inizioMs,
                fineMs = s.fineMs,
                testo = s.testo,
                inRiproduzione = inRiproduzione(s.inizioMs, s.fineMs, statoLettore),
            )
        }
    }

    // AC-208/L573d: a Segmento is highlighted while the shared player's position for THIS Registrazione
    // sits inside its own [inizioMs, fineMs) — by POSITION alone, not gated on `s.inRiproduzione`. A
    // pause leaves the position exactly where it was; requiring `inRiproduzione` too made the highlight
    // vanish on pause even though the same Segmento is still the one the header bar shows paused on.
    // Overlapping Segmenti (ux-proposal Q-4) may both highlight at once, both kept, no warning: the same
    // rule the transcript's own ordering already lives with.
    private fun inRiproduzione(inizioMs: Long, fineMs: Long, s: StatoLettore): Boolean =
        s.registrazioneId == registrazioneId && s.posizioneMs in inizioMs until fineMs

    // L573b: `disponibilitaIncerta` wins over `disponibile` — a thrown `lettore.disponibile()` must
    // read as a retry-enabled `Errore`, never as the retry-disabled `NonDisponibile` a clean `false`
    // gets.
    private fun barraDi(s: StatoLettore, disponibile: Boolean): LettoreUiStato = when {
        disponibilitaIncerta -> LettoreUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)
        !disponibile -> LettoreUiStato.NonDisponibile(MESSAGGIO_AUDIO_NON_DISPONIBILE)
        s.registrazioneId == registrazioneId -> LettoreUiStato.Pronto(s.posizioneMs, s.inRiproduzione)
        else -> LettoreUiStato.Inattivo
    }

    // AC-217/AC-343-style: `disponibile` is checked once in `carica()` and kept STICKY here
    // (`Dati.audioDisponibile`) — every later tick of `lettore.stato` only reflects position/play-pause,
    // never re-decides availability. L573b: a tick that actually reports THIS Registrazione settles
    // `disponibilitaIncerta` — the port just proved it can talk about this source either way.
    private fun rifletti(s: StatoLettore) = aggiornaDati { dati ->
        if (s.registrazioneId == registrazioneId) disponibilitaIncerta = false
        dati.copy(
            barra = barraDi(s, dati.audioDisponibile),
            segmenti = dati.segmenti.map { seg ->
                seg.copy(inRiproduzione = inRiproduzione(seg.inizioMs, seg.fineMs, s))
            },
        )
    }

    /** AC-208: plays [id]'s Segmento from its `inizio`. AC-217: a no-op while the audio source is
     * missing — the transcript stays readable, nothing else changes. */
    fun riproduciSegmento(id: SegmentoId) {
        val dati = _stato.value as? RegistrazioneUiStato.Dati ?: return
        if (!dati.audioDisponibile) return
        dati.segmenti.find { it.segmentoId == id }?.let { segmento ->
            avviaLettore { lettore.riproduciDa(registrazioneId, segmento.inizioMs) }
        }
    }

    /** The header audio bar's own '▶' (AC-217: a no-op while the audio source is missing). */
    fun riproduciDaInizio() {
        val dati = _stato.value as? RegistrazioneUiStato.Dati ?: return
        if (!dati.audioDisponibile) return
        avviaLettore { lettore.riproduciDa(registrazioneId, 0) }
    }

    /** The header audio bar's own pause. */
    fun pausa() = avviaLettore { lettore.pausa() }

    /** AC-218: opens the Sbobinatura with the OS default app. A no-op while
     * [RegistrazioneUiStato.Dati.sbobinaturaPercorso] has not resolved yet. */
    fun apriSbobinatura() = conSbobinatura(apriEsterno::apriFile)

    /** AC-218: reveals the Sbobinatura in a file manager window. Same guard as [apriSbobinatura]. */
    fun mostraSbobinaturaNellaCartella() = conSbobinatura(apriEsterno::mostraNellaCartella)

    private fun conSbobinatura(azione: (String) -> Unit) {
        val percorso = (_stato.value as? RegistrazioneUiStato.Dati)?.sbobinaturaPercorso ?: return
        avvia { azione(percorso) }
    }

    // H2: every port call this presenter fires off the main flow runs here, wrapped the same way as
    // every other presenter in this codebase (RegistrazioniPresenter/LettorePresenter) — a fault never
    // escapes `scope.launch` and takes the `stato`/`lettore.stato` collectors down with it.
    private fun eseguiSuLane(dispatcher: CoroutineDispatcher, operazione: () -> Unit): Job = scope.launch {
        try {
            withContext(dispatcher) { operazione() }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            aggiornaDati { it.copy(errore = MESSAGGIO_ERRORE_GENERICO) }
        }
    }

    private fun avvia(operazione: () -> Unit): Job = eseguiSuLane(io, operazione)

    // L573a: every command that reaches the shared `lettore` (segmento/estratto/pausa/header-play) runs
    // on [ioLettore] (the single lane), never on the plain, possibly multi-threaded [io] — click order
    // is enforced by the LANE, not by cancellation. Cancellation only drops a command that is still
    // queued (not yet started): a new one always cancels whatever Job is recorded here, so a click that
    // never reached the port at all is skipped outright, exactly the fix `LettorePresenter`'s own
    // HIGH-1 already applies for its single shared instance.
    private fun avviaLettore(operazione: () -> Unit) {
        comandoLettoreInCorso?.cancel()
        comandoLettoreInCorso = eseguiSuLane(ioLettore, operazione)
    }

    /** AC-403: '▶ estratto' of a Voce card — a no-op while the audio source is missing. */
    fun riproduciEstrattoVoce(voceId: VoceId) {
        if ((_stato.value as? RegistrazioneUiStato.Dati)?.audioDisponibile != true) return
        avviaLettore { parlanti.estratto(VoceRef(registrazioneId, voceId))?.let(lettore::riproduciEstratto) }
    }

    /** AC-403: '▶' of a Candidato (its own past excerpt) — a no-op while the audio source is missing. */
    fun riproduciEstratto(estratto: EstrattoRef) {
        if ((_stato.value as? RegistrazioneUiStato.Dati)?.audioDisponibile != true) return
        avviaLettore { lettore.riproduciEstratto(estratto) }
    }

    /** H1: dismisses the current inline `errore`, if any. */
    fun chiudiErrore() = aggiornaDati { it.copy(errore = null) }

    /** AC-S120/S121: switches the centre-column tab, kept per window in [selezioneSchedaS3]. */
    fun selezionaScheda(scheda: SchedaS3) {
        selezioneSchedaS3.seleziona(scheda)
        aggiornaDati { it.copy(schedaSelezionata = scheda) }
    }

    private fun aggiornaDati(f: (RegistrazioneUiStato.Dati) -> RegistrazioneUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is RegistrazioneUiStato.Dati) _stato.value = f(attuale)
    }

    val azioni: AzioniRegistrazione = AzioniRegistrazione(
        riproduciDaInizio = ::riproduciDaInizio,
        pausa = ::pausa,
        riproduciSegmento = ::riproduciSegmento,
        apriSbobinatura = ::apriSbobinatura,
        mostraSbobinaturaNellaCartella = ::mostraSbobinaturaNellaCartella,
        chiudiErrore = ::chiudiErrore,
        riprova = ::riprova,
        selezionaSegmento = voci::selezionaSegmento,
        deseleziona = voci::deseleziona,
        dividiVoce = voci::dividiVoce,
        riassegnaA = voci::riassegnaA,
        unisci = voci::unisci,
        conferma = voci::conferma,
        confermaParlante = voci::confermaParlante,
        nuovoParlante = voci::nuovoParlante,
        salta = voci::salta,
        annullaComando = voci::annulla,
        chiudiErroreVoce = voci::chiudiErrore,
        riproduciEstrattoVoce = ::riproduciEstrattoVoce,
        riproduciEstratto = ::riproduciEstratto,
        nominaFrase = voci::nominaFrase,
        togliConferma = voci::togliConferma,
        annullaFrase = voci::annullaFrase,
        calcolaSomiglianza = voci::calcolaSomiglianza,
        applicaSomiglianza = voci::applicaSomiglianza,
        annullaSomiglianza = voci::annullaSomiglianza,
        selezionaScheda = ::selezionaScheda,
    )

    companion object {
        /** ADR 0017 §3 (AC-412/AC-416): the ONLY definition of the visible-wait threshold. */
        const val SOGLIA_ATTESA_VISIBILE_MS: Long = 2_000
    }
}
