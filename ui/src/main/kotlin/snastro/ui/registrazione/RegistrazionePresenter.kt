package snastro.ui.registrazione

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.ApriEsterno
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO

/**
 * State holder of S3 · Registrazione, READ-ONLY in R1 (RC-2, thin UI; AC-207/208/217/218). Joins
 * `trascritto-view` ([trascritto]) with the Documento's resolved path ([documento]) and reflects the
 * shared [lettore] (AC-208: which Segmento is currently playing; AC-217: the header audio bar disabled,
 * with a message, when the source is missing — clicking a Segmento then does nothing, the transcript
 * itself stays readable).
 *
 * AC-402: constructed with ONLY these four collaborators (plus [registrazioneId]/[scope]/[io]) — the
 * Parlanti sources (identificazione-voci, proposta, proposta-unione, parlanti-attivi, estratto-audio)
 * and the ConfermaAttribuzione/SaltaVoce/UnisciVoci/DividiVoce/RiassegnaSegmento commands are simply
 * ABSENT in R1: no Voci panel, no card, no merge banner, no 'conferma'/'salta'/'cambia'/'Unisci con', no
 * selection, no '▶ estratto'. The R2 block `schermata-registrazione-identificazione` extends this
 * presenter with those later — not pre-declared here per YAGNI (frugality rung 1): none of their
 * read-models/commands is buildable yet (still `todo`), so a speculative optional parameter today would
 * be dead code with no test to justify it.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([trascritto]/[documento]) rather than the
 * concrete query classes, mirroring `RegistrazioniPresenter` (dev-architecture `#presenter`): `:avvio`
 * binds the real shape (`TrascrittoQuery::vista` bound to [registrazioneId]; the Documento's absolute
 * path under `documenti/`, joined from `documento`'s `nomeFile` and the open Progetto's folder) — this
 * presenter's own test doubles stay plain lambdas over Published-Language values, never a `*:dominio`
 * type (CR-1(b)).
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
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RegistrazioneUiStato>(RegistrazioneUiStato.Caricamento)
    val stato: StateFlow<RegistrazioneUiStato> = _stato.asStateFlow()

    init {
        scope.launch { carica() }
        scope.launch { lettore.stato.collect { s -> rifletti(s) } }
    }

    private suspend fun carica() {
        try {
            val vista = withContext(io) { trascritto() }
            if (vista == null) {
                _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
                return
            }
            val percorso = withContext(io) { documento() }
            val disponibile = withContext(io) { lettore.disponibile(registrazioneId) }
            val statoLettore = lettore.stato.value
            _stato.value = RegistrazioneUiStato.Dati(
                titolo = vista.titolo,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
                segmenti = segmentiDi(vista, statoLettore),
                barra = barraDi(statoLettore, disponibile),
                audioDisponibile = disponibile,
                documentoPercorso = percorso,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Same rationale as every other presenter in this codebase (RegistrazioniPresenter/ShellPresenter):
            // a fault of trascritto/documento/lettore never leaves this screen stuck on Caricamento.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            _stato.value = RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)
        }
    }

    /** Retries the initial load after [RegistrazioneUiStato.Errore]. */
    fun riprova() {
        scope.launch { carica() }
    }

    private fun segmentiDi(vista: TrascrittoView, statoLettore: StatoLettore): List<SegmentoRiga> {
        val etichette = vista.voci.associate { it.voceId to it.etichetta }
        return vista.segmenti.map { s ->
            SegmentoRiga(
                segmentoId = s.segmentoId,
                voceId = s.voceId,
                etichettaVoce = etichette[s.voceId] ?: "Voce ${s.voceId.numero}",
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
    )
}
