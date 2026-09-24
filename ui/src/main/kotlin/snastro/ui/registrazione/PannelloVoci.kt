package snastro.ui.registrazione

import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione

/**
 * R2 Voci panel of S3 (ux-proposal S3, AC-209..219/318/319/403..405/411..417) — built by
 * [RegistrazionePresenter] only when [SorgentiParlanti] are supplied; `null` in R1 (AC-402).
 * [parlantiAttivi] feeds 'altri ▾' / 'cambia'; [unioni] the merge banners (AC-216);
 * [estrattiDisponibili] is `false` when the audio source is missing (AC-403: every '▶' disabled, with
 * [MESSAGGIO_ESTRATTI_NON_DISPONIBILI][snastro.ui.testi.MESSAGGIO_ESTRATTI_NON_DISPONIBILI]).
 */
data class PannelloVoci(
    val carte: List<CartaVoce>,
    val parlantiAttivi: List<ParlanteAttivo>,
    val unioni: List<PropostaDiUnione>,
    val estrattiDisponibili: Boolean,
    val unioneAbilitata: Boolean,
)

/** A Voce as a target of 'Unisci con ▾' / 'Riassegna a ▾': its number and its label (Nome or "Voce n"). */
data class OpzioneVoce(val voceId: VoceId, val etichetta: String)

/** AC-411/AC-412: [IN_CORSO] at once on click; [IN_ATTESA] once past `SOGLIA_ATTESA_VISIBILE_MS` ('Annulla'). */
enum class AttesaComando { IN_CORSO, IN_ATTESA }

/**
 * One card of the panel, in label order. [titolo] is always "Voce n"; the attributed Nome is in
 * [contenuto]. [inCorso] ≠ `null` disables every card action (AC-411) — [azioniAbilitate] says so once.
 * [errore] is the dismissible inline message of the last failed card command (AC-215/AC-318).
 * [altreVoci] are the 'Unisci con ▾' targets (this card survives). [soloLettura] (ADR 0018 Amendment
 * (b) §2, AC-454) disables every card action while a re-run is queued/running — '▶ estratto' is NOT
 * one of them (it stays governed by [PannelloVoci.estrattiDisponibili] alone).
 */
data class CartaVoce(
    val voceId: VoceId,
    val titolo: String,
    val contenuto: ContenutoCarta,
    val inCorso: AttesaComando? = null,
    val errore: String? = null,
    val altreVoci: List<OpzioneVoce> = emptyList(),
    val soloLettura: Boolean = false,
) {
    /** AC-411/AC-454: no second command from a card while one runs, nothing to act on while
     * loading/failed, and no command at all while read-only. */
    val azioniAbilitate: Boolean
        get() = inCorso == null && !soloLettura &&
            (contenuto is ContenutoCarta.Attribuita || contenuto is ContenutoCarta.DaIdentificare)

    /** 'Conferma' needs a top Candidato (AC-416: not while the Proposta is still in attesa). */
    val confermaAbilitata: Boolean
        get() = azioniAbilitate &&
            ((contenuto as? ContenutoCarta.DaIdentificare)?.proposta as? StatoProposta.Pronta)
                ?.candidati?.isNotEmpty() == true
}

/**
 * The transcript selection toolbar (AC-209..211): the selection is always inside ONE Voce ([voceId]).
 * [dividiAbilitato] is `false`, with [spiegazioneDividi], when the selection is the whole Voce
 * (INV-10); [destinazioni] are the OTHER Voci ('Riassegna a ▾', plus 'nuova voce').
 */
data class BarraSelezione(
    val voceId: VoceId,
    val etichetta: String,
    val numeroSegmenti: Int,
    val dividiAbilitato: Boolean,
    val spiegazioneDividi: String?,
    val destinazioni: List<OpzioneVoce>,
    val abilitata: Boolean,
)
