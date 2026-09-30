package snastro.ui.registrazione

import snastro.kernel.EstrattoRef
import snastro.kernel.ParlanteId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista

/**
 * One lambda per user action of S3 · Registrazione (dev-architecture `#presenter`, user decision K-c).
 * [riproduciDaInizio]/[pausa] back the header audio bar; [riproduciSegmento] is AC-208's click/'▶' on a
 * Segmento (a no-op when the audio source is missing, AC-217 — enforced by the presenter, not just by
 * disabling the control).
 *
 * The Voci panel and Revisione actions below default to no-ops so a fixture that only exercises a few
 * of them (render-check, a narrow presenter test) can omit the rest (frugality) — every one of them is
 * always wired in the running app (ADR 0030 §1, U1).
 */
@Suppress("LongParameterList") // one lambda per user action (K-c)
data class AzioniRegistrazione(
    val riproduciDaInizio: () -> Unit,
    val pausa: () -> Unit,
    val riproduciSegmento: (SegmentoId) -> Unit,
    val apriSbobinatura: () -> Unit,
    val mostraSbobinaturaNellaCartella: () -> Unit,
    val chiudiErrore: () -> Unit,
    val riprova: () -> Unit,
    val selezionaSegmento: (SegmentoId) -> Unit = {},
    val deseleziona: () -> Unit = {},
    val dividiVoce: () -> Unit = {},
    /** `null` = 'nuova voce'. */
    val riassegnaA: (VoceId?) -> Unit = {},
    /** (sopravvive, rimossa). */
    val unisci: (VoceId, VoceId) -> Unit = { _, _ -> },
    val conferma: (VoceId) -> Unit = {},
    val confermaParlante: (VoceId, ParlanteId) -> Unit = { _, _ -> },
    val nuovoParlante: (VoceId, String, TipoParlanteVista) -> Unit = { _, _, _ -> },
    val salta: (VoceId) -> Unit = {},
    val annullaComando: (VoceId) -> Unit = {},
    val chiudiErroreVoce: (VoceId) -> Unit = {},
    val riproduciEstrattoVoce: (VoceId) -> Unit = {},
    val riproduciEstratto: (EstrattoRef) -> Unit = {},
    /** ADR 0019 §5/§6: 'Dai un nome a questa frase ▾' on the ONE selected Segmento. */
    val nominaFrase: (ObiettivoNome) -> Unit = {},
    val togliConferma: () -> Unit = {},
    val annullaFrase: (SegmentoId) -> Unit = {},
    /** 'Riassegna per somiglianza' and 'Ricalcola'. */
    val calcolaSomiglianza: () -> Unit = {},
    val applicaSomiglianza: () -> Unit = {},
    /** 'Annulla' (computation or preview), 'Chiudi' (N = 0) and the result message's dismissal. */
    val annullaSomiglianza: () -> Unit = {},
    /** AC-S120/S121: switches the centre-column tab. */
    val selezionaScheda: (SchedaS3) -> Unit = {},
)
