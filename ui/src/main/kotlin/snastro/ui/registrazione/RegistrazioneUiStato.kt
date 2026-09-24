package snastro.ui.registrazione

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.ui.lettore.LettoreUiStato
import java.time.LocalDate

/**
 * State of S3 · Registrazione, READ-ONLY in R1 (AC-207/208/217/218/402) — presenter-owned, rendered by
 * [SchermataRegistrazione]. The Voci panel, the selection and the Revisione UI are R2 (block
 * `schermata-registrazione-identificazione`): [Dati.pannello] is `null` in R1 (AC-402).
 */
sealed interface RegistrazioneUiStato {
    /** AC-207: the trascritto is still loading — the transcript area renders a skeleton, not a blank screen. */
    data object Caricamento : RegistrazioneUiStato

    /**
     * The loaded trascritto. [segmenti] are in time order across Voci, exactly [TrascrittoView]'s own
     * order (INV-7/INV-8, never re-sorted here) — an empty list is a real "nessun parlato rilevato"
     * catalog, rendered as a dedicated message, not [Errore]. [barra] reflects the shared [LettoreAudio]
     * for this Registrazione only ([RegistrazionePresenter.registrazioneId]); [documentoPercorso] is
     * `null` only while it has not resolved yet — 'Apri documento'/'Mostra nella cartella' are disabled
     * then (AC-218). [errore] is a dismissible inline message for the last failed
     * riproduzione/apertura (H1 pattern), never replacing [segmenti].
     *
     * ADR 0018 (optional `stati` source, AC-452): [soloLettura] is `true` while the latest Elaborazione
     * of this Registrazione is `in_attesa`/`in_corso` (a re-run over the Trascritto shown here);
     * [bannerRitrascrizione] is the R1 two-line banner text, `null` unless [soloLettura] (also `null`
     * without the optional `stati` source — R1 test: never read-only). [bannerRitrascrizionePannello]
     * is owned and set ONLY by the R2 panel (`schermata-registrazione-identificazione`, AC-454's third
     * banner line) — the base presenter never writes it.
     */
    @Suppress("LongParameterList") // one field per AC-207/208/217/218/402/452 datum of the screen
    data class Dati(
        val titolo: String,
        val dataRegistrazione: LocalDate,
        val durataMs: Long,
        val segmenti: List<SegmentoRiga>,
        val barra: LettoreUiStato,
        val audioDisponibile: Boolean,
        val documentoPercorso: String?,
        val errore: String? = null,
        /** R2 only (AC-402: `null` in R1 — no panel at all). */
        val pannello: PannelloVoci? = null,
        /** AC-209: the selected Segmenti, always of one Voce (R2 only). */
        val selezione: Set<SegmentoId> = emptySet(),
        /** AC-209..211: the selection toolbar, `null` without a selection. */
        val barraSelezione: BarraSelezione? = null,
        val soloLettura: Boolean = false,
        val bannerRitrascrizione: String? = null,
        val bannerRitrascrizionePannello: String? = null,
    ) : RegistrazioneUiStato

    /** M5-style: the INITIAL load failed (a thrown fault, or no Trascritto at all for this Registrazione) —
     * a distinct state with a retry action, never the misleading "nessun parlato" empty message. */
    data class Errore(val messaggio: String) : RegistrazioneUiStato
}

/**
 * One row of the transcript (AC-207/208/217): [etichettaVoce] is trascritto-view's own "Voce n" label
 * (never a Nome — R2 only, AC-402); the color dot is computed at render time from [voceId]
 * ([snastro.ui.palette], a pure function of the number — not presenter state). [inRiproduzione]
 * (AC-208) is `true` while the shared player plays this Registrazione with a position inside
 * `[inizioMs, fineMs)` — computed live from [snastro.ui.lettore.StatoLettore], never re-decided by a
 * click (a click only asks [snastro.ui.lettore.LettoreAudio] to play from [inizioMs]).
 *
 * R2 only (ADR 0019 §6, set by the Voci panel's presenter half; always the defaults in R1): [confermato]
 * renders the pin (AC-528); [attesaFrase] is the pending state of a 'Dai un nome a questa frase' on this
 * Segmento (AC-529, ADR 0017 §3).
 */
data class SegmentoRiga(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val etichettaVoce: String,
    val inizioMs: Long,
    val fineMs: Long,
    val testo: String,
    val inRiproduzione: Boolean = false,
    val confermato: Boolean = false,
    val attesaFrase: AttesaComando? = null,
)
