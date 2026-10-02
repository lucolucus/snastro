package snastro.ui.registrazione

import androidx.compose.runtime.Composable
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.stile.SegnoScheda
import java.time.LocalDate

/**
 * State of S3 · Registrazione (AC-207/208/217/218) — presenter-owned, rendered by
 * [SchermataRegistrazione]. The Voci panel, the selection and the Revisione UI ([Dati.pannello]) are
 * published asynchronously by [StatoVoci] once the transcript is loaded — `null` only before that
 * first publish (AC-402).
 */
sealed interface RegistrazioneUiStato {
    /** AC-207: the trascritto is still loading — the transcript area renders a skeleton, not a blank screen. */
    data object Caricamento : RegistrazioneUiStato

    /**
     * The loaded trascritto. [segmenti] are in time order across Voci, exactly [TrascrittoView]'s own
     * order (INV-7/INV-8, never re-sorted here) — an empty list is a real "nessun parlato rilevato"
     * catalog, rendered as a dedicated message, not [Errore]. [barra] reflects the shared [LettoreAudio]
     * for this Registrazione only ([RegistrazionePresenter.registrazioneId]); [sbobinaturaPercorso] is
     * `null` only while it has not resolved yet — 'Apri sbobinatura'/'Mostra nella cartella' are disabled
     * then (AC-218). [errore] is a dismissible inline message for the last failed
     * riproduzione/apertura (H1 pattern), never replacing [segmenti].
     *
     * ADR 0018 (`stati` source, AC-452): [soloLettura] is `true` while the latest Elaborazione of this
     * Registrazione is `in_attesa`/`in_corso` (a re-run over the Trascritto shown here); [bannerRitrascrizione]
     * is the two-line banner text, `null` unless [soloLettura]. [bannerRitrascrizionePannello] is owned and
     * set ONLY by the Voci panel ([StatoVoci], AC-454's third banner line) — the base presenter never writes it.
     */
    @Suppress("LongParameterList") // one field per AC-207/208/217/218/402/452 datum of the screen
    data class Dati(
        val titolo: String,
        val dataRegistrazione: LocalDate,
        val durataMs: Long,
        val segmenti: List<SegmentoRiga>,
        val barra: LettoreUiStato,
        val audioDisponibile: Boolean,
        val sbobinaturaPercorso: String?,
        val errore: String? = null,
        /** AC-402: `null` only before [StatoVoci]'s first publish — no panel yet. */
        val pannello: PannelloVoci? = null,
        /** AC-209: the selected Segmenti, always of one Voce. */
        val selezione: Set<SegmentoId> = emptySet(),
        /** AC-209..211: the selection toolbar, `null` without a selection. */
        val barraSelezione: BarraSelezione? = null,
        val soloLettura: Boolean = false,
        val bannerRitrascrizione: String? = null,
        val bannerRitrascrizionePannello: String? = null,
        /** AC-S120: the Riassunto tab's own content, bound to this Registrazione by the presenter
         * ([SorgenteRiassuntoS3.contenuto] partially applied). MANDATORY, never nullable — ADR 0030 §1,
         * U1: the single composition always wires it, so a fixture/test builds one too (pre-release
         * finding #83: a nullable field here was a release-flag leftover from before ADR 0030). */
        val contenutoRiassunto: @Composable () -> Unit,
        /** AC-S121: which tab is shown, kept per window ([SelezioneSchedaS3]) — irrelevant while
         * [contenutoRiassunto] is `null` (no tabs to select between). */
        val schedaSelezionata: SchedaS3 = SchedaS3.TRASCRIZIONE,
        /** AC-S122: the small mark after the 'Riassunto' label, fed by [SorgenteRiassuntoS3.segno]. */
        val segnoRiassunto: SegnoScheda? = null,
        /** AC-I74: the multi-part header + switcher; `null` on a 1-Parte Incontro (INV-I3). */
        val parte: IntestazioneParte? = null,
    ) : RegistrazioneUiStato {
        /**
         * AC-S123 (ux-proposal "Banner precedence on S3"): the ONE screen [BannerSchermata], by
         * precedence — read-only during a Ritrascrivi ([bannerRitrascrizione]) > the audio source
         * missing ([audioDisponibile]) > 'n voci da identificare' ([pannello]). A PURE, computed
         * predicate (dev-architecture named-predicate pattern) — never stored, so it can never drift
         * from the fields above, and is table-testable on plain [Dati] fixtures.
         */
        val bannerSchermata: BannerSchermata?
            get() = when {
                bannerRitrascrizione != null ->
                    BannerSchermata.Ritrascrizione(bannerRitrascrizione, bannerRitrascrizionePannello)
                !audioDisponibile -> BannerSchermata.AudioMancante
                else ->
                    pannello?.carte
                        ?.count { it.contenuto is ContenutoCarta.DaIdentificare }
                        ?.takeIf { it > 0 }
                        ?.let { BannerSchermata.VociDaIdentificare(it) }
            }
    }

    /** M5-style: the INITIAL load failed (a thrown fault, or no Trascritto at all for this Registrazione) —
     * a distinct state with a retry action, never the misleading "nessun parlato" empty message. */
    data class Errore(val messaggio: String) : RegistrazioneUiStato
}

/**
 * One row of the transcript (AC-207/208/217): [etichettaVoce] is trascritto-view's own "Voce n" label
 * until [StatoVoci] resolves a Nome (AC-402); the color dot is computed at render time from [voceId]
 * ([snastro.ui.palette], a pure function of the number — not presenter state). [inRiproduzione]
 * (AC-208) is `true` while the shared player plays this Registrazione with a position inside
 * `[inizioMs, fineMs)` — computed live from [snastro.ui.lettore.StatoLettore], never re-decided by a
 * click (a click only asks [snastro.ui.lettore.LettoreAudio] to play from [inizioMs]).
 *
 * ADR 0019 §6, set by the Voci panel's presenter half ([StatoVoci], defaults otherwise): [confermato]
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
