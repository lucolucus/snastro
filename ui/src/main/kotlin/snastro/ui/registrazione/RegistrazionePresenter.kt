package snastro.ui.registrazione

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO

/**
 * State holder of S3 · Registrazione, READ-ONLY in R1 (RC-2, thin UI; AC-207/208/217/218). Joins
 * `trascritto-view` ([trascritto]) with the Documento's resolved path ([documento]) and reflects the
 * shared [lettore] (AC-208: which Segmento is currently playing; AC-217: the header audio bar disabled,
 * with a message, when the source is missing — clicking a Segmento then does nothing, the transcript
 * itself stays readable).
 *
 * AC-402: in R1 it is constructed with ONLY these four collaborators (plus [registrazioneId]/[scope]/[io])
 * — no Voci panel, no card, no merge banner, no selection, no '▶ estratto'. R2
 * (`schermata-registrazione-identificazione`) supplies the OPTIONAL [parlanti] ([SorgentiParlanti]):
 * the Voci panel, the Nome labels, the selection toolbar and the Revisione commands, all handled by
 * [StatoVoci] into this presenter's own [stato]. [SOGLIA_ATTESA_VISIBILE_MS] (ADR 0017 §3) is defined
 * here, once.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([trascritto]/[documento]) rather than the
 * concrete query classes, mirroring `RegistrazioniPresenter` (dev-architecture `#presenter`): `:avvio`
 * binds the real shape (`TrascrittoQuery::vista` bound to [registrazioneId]; the Documento's absolute
 * path under `documenti/`, joined from `documento`'s `nomeFile` and the open Progetto's folder) — this
 * presenter's own test doubles stay plain lambdas over Published-Language values, never a `*:dominio`
 * type (CR-1(b)).
 *
 * ADR 0018 (AC-452/453, optional [stati]/[aggiornamenti]): while the latest Elaborazione of
 * [registrazioneId] is `in_attesa`/`in_corso` (a re-run over the Trascritto shown here), the screen is
 * READ-ONLY — [RegistrazioneUiStato.Dati.soloLettura] + the banner. [aggiornamenti] (R15, `tec-shell-ui`)
 * reloads on the Cambiamento the replacement/cancellation publishes, so the read-only flag, the banner
 * and (on a replacement) the transcript itself stay current (AC-453). Both default to `null`: R1
 * (`avvio-composizione`) supplies neither, so a row is never read-only there.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class RegistrazionePresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val trascritto: () -> TrascrittoView?,
    private val documento: () -> String?,
    private val lettore: LettoreAudio,
    private val apriEsterno: ApriEsterno,
    private val parlanti: SorgentiParlanti? = null,
    private val stati: (() -> StatoRegistrazioneVista?)? = null,
    private val aggiornamenti: AggiornamentiVista? = null,
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RegistrazioneUiStato>(RegistrazioneUiStato.Caricamento)
    val stato: StateFlow<RegistrazioneUiStato> = _stato.asStateFlow()

    // R2 (schermata-registrazione-identificazione): the Voci panel, the Nome labels, the selection
    // toolbar and the Revisione commands — absent in R1 (AC-402), so nothing of it runs there.
    private val voci: StatoVoci? = parlanti?.let { sorgenti ->
        StatoVoci(sorgenti, scope, io, registrazioneId, trascritto, _stato) { v -> segmentiDi(v, lettore.stato.value) }
    }

    init {
        scope.launch { carica() }
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
        // AC-453: reloads on any Cambiamento of this Registrazione — the replacement/cancellation event
        // included (R15, same pattern as RegistrazioniPresenter/ParlantiPresenter).
        aggiornamenti?.let { a ->
            scope.launch {
                a.cambiamenti.collect { c ->
                    if (c.registrazioneId == null || c.registrazioneId == registrazioneId) carica()
                }
            }
        }
        voci?.avvia()
    }

    private suspend fun carica() {
        try {
            // AC-453/455: a reload never keeps a selection or a panel from a previous generation.
            voci?.deseleziona()
            val vista = withContext(io) { trascritto() }
            if (vista == null) {
                _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
                return
            }
            val percorso = withContext(io) { documento() }
            val disponibile = withContext(io) { lettore.disponibile(registrazioneId) }
            val statoLettore = lettore.stato.value
            val soloLettura = soloLetturaDi(withContext(io) { stati?.invoke() })
            voci?.vista = vista
            _stato.value = RegistrazioneUiStato.Dati(
                titolo = vista.titolo,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
                segmenti = segmentiDi(vista, statoLettore),
                barra = barraDi(statoLettore, disponibile),
                audioDisponibile = disponibile,
                documentoPercorso = percorso,
                soloLettura = soloLettura,
                bannerRitrascrizione = if (soloLettura) MESSAGGIO_RITRASCRIZIONE_IN_CORSO else null,
            )
            voci?.pubblica()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Same rationale as every other presenter in this codebase (RegistrazioniPresenter/ShellPresenter):
            // a fault of trascritto/documento/lettore never leaves this screen stuck on Caricamento.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
        }
        if (_stato.value is RegistrazioneUiStato.Dati) voci?.ricaricaParlanti()
    }

    /** Retries the initial load after [RegistrazioneUiStato.Errore]. */
    fun riprova() {
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
                etichettaVoce = etichettaDiVoce(vista, s.voceId, voci?.nomeDi(s.voceId)),
                inizioMs = s.inizioMs,
                fineMs = s.fineMs,
                testo = s.testo,
                inRiproduzione = inRiproduzione(s.inizioMs, s.fineMs, statoLettore),
            )
        }
    }

    // AC-208: a Segmento is highlighted while the shared player plays THIS Registrazione with a position
    // inside its own [inizioMs, fineMs) — overlapping Segmenti (ux-proposal Q-4) may both highlight at
    // once, both kept, no warning: the same rule the transcript's own ordering already lives with.
    private fun inRiproduzione(inizioMs: Long, fineMs: Long, s: StatoLettore): Boolean =
        s.registrazioneId == registrazioneId && s.inRiproduzione && s.posizioneMs in inizioMs until fineMs

    private fun barraDi(s: StatoLettore, disponibile: Boolean): LettoreUiStato = when {
        !disponibile -> LettoreUiStato.NonDisponibile(MESSAGGIO_AUDIO_NON_DISPONIBILE)
        s.registrazioneId == registrazioneId -> LettoreUiStato.Pronto(s.posizioneMs, s.inRiproduzione)
        else -> LettoreUiStato.Inattivo
    }

    // AC-217/AC-343-style: `disponibile` is checked once in `carica()` and kept STICKY here
    // (`Dati.audioDisponibile`) — every later tick of `lettore.stato` only reflects position/play-pause,
    // never re-decides availability.
    private fun rifletti(s: StatoLettore) = aggiornaDati { dati ->
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
            avvia { lettore.riproduciDa(registrazioneId, segmento.inizioMs) }
        }
    }

    /** The header audio bar's own '▶' (AC-217: a no-op while the audio source is missing). */
    fun riproduciDaInizio() {
        val dati = _stato.value as? RegistrazioneUiStato.Dati ?: return
        if (!dati.audioDisponibile) return
        avvia { lettore.riproduciDa(registrazioneId, 0) }
    }

    /** The header audio bar's own pause. */
    fun pausa() = avvia { lettore.pausa() }

    /** AC-218: opens the Documento with the OS default app. A no-op while
     * [RegistrazioneUiStato.Dati.documentoPercorso] has not resolved yet. */
    fun apriDocumento() = conDocumento(apriEsterno::apriFile)

    /** AC-218: reveals the Documento in a file manager window. Same guard as [apriDocumento]. */
    fun mostraDocumentoNellaCartella() = conDocumento(apriEsterno::mostraNellaCartella)

    private fun conDocumento(azione: (String) -> Unit) {
        val percorso = (_stato.value as? RegistrazioneUiStato.Dati)?.documentoPercorso ?: return
        avvia { azione(percorso) }
    }

    // H2: every port call this presenter fires off the main flow runs here, wrapped the same way as
    // every other presenter in this codebase (RegistrazioniPresenter/LettorePresenter) — a fault never
    // escapes `scope.launch` and takes the `stato`/`lettore.stato` collectors down with it.
    private fun avvia(operazione: () -> Unit) {
        scope.launch {
            try {
                withContext(io) { operazione() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaDati { it.copy(errore = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** AC-403: '▶ estratto' of a Voce card — a no-op while the audio source is missing. */
    fun riproduciEstrattoVoce(voceId: VoceId) {
        val sorgenti = parlanti ?: return
        if ((_stato.value as? RegistrazioneUiStato.Dati)?.audioDisponibile != true) return
        avvia { sorgenti.estratto(VoceRef(registrazioneId, voceId))?.let(lettore::riproduciEstratto) }
    }

    /** AC-403: '▶' of a Candidato (its own past excerpt) — a no-op while the audio source is missing. */
    fun riproduciEstratto(estratto: EstrattoRef) {
        if (parlanti == null) return
        if ((_stato.value as? RegistrazioneUiStato.Dati)?.audioDisponibile != true) return
        avvia { lettore.riproduciEstratto(estratto) }
    }

    /** H1: dismisses the current inline `errore`, if any. */
    fun chiudiErrore() = aggiornaDati { it.copy(errore = null) }

    private fun aggiornaDati(f: (RegistrazioneUiStato.Dati) -> RegistrazioneUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is RegistrazioneUiStato.Dati) _stato.value = f(attuale)
    }

    val azioni: AzioniRegistrazione = AzioniRegistrazione(
        riproduciDaInizio = ::riproduciDaInizio,
        pausa = ::pausa,
        riproduciSegmento = ::riproduciSegmento,
        apriDocumento = ::apriDocumento,
        mostraDocumentoNellaCartella = ::mostraDocumentoNellaCartella,
        chiudiErrore = ::chiudiErrore,
        riprova = ::riprova,
        selezionaSegmento = { id -> voci?.selezionaSegmento(id) },
        deseleziona = { voci?.deseleziona() },
        dividiVoce = { voci?.dividiVoce() },
        riassegnaA = { destinazione -> voci?.riassegnaA(destinazione) },
        unisci = { sopravvive, rimossa -> voci?.unisci(sopravvive, rimossa) },
        conferma = { voceId -> voci?.conferma(voceId) },
        confermaParlante = { voceId, parlanteId -> voci?.confermaParlante(voceId, parlanteId) },
        nuovoParlante = { voceId, nome, tipo -> voci?.nuovoParlante(voceId, nome, tipo) },
        salta = { voceId -> voci?.salta(voceId) },
        annullaComando = { voceId -> voci?.annulla(voceId) },
        chiudiErroreVoce = { voceId -> voci?.chiudiErrore(voceId) },
        riproduciEstrattoVoce = ::riproduciEstrattoVoce,
        riproduciEstratto = ::riproduciEstratto,
        nominaFrase = { obiettivo -> voci?.nominaFrase(obiettivo) },
        togliConferma = { voci?.togliConferma() },
        annullaFrase = { segmento -> voci?.annullaFrase(segmento) },
        calcolaSomiglianza = { voci?.calcolaSomiglianza() },
        applicaSomiglianza = { voci?.applicaSomiglianza() },
        annullaSomiglianza = { voci?.annullaSomiglianza() },
    )

    companion object {
        /** ADR 0017 §3 (AC-412/AC-416): the ONLY definition of the visible-wait threshold. */
        const val SOGLIA_ATTESA_VISIBILE_MS: Long = 2_000
    }
}
