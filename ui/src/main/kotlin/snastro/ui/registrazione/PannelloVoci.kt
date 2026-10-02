package snastro.ui.registrazione

import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione

/**
 * Voci panel of S3 (ux-proposal S3, AC-209..219/318/319/403..405/411..417) — published by [StatoVoci]
 * into [RegistrazioneUiStato.Dati.pannello] once the transcript is loaded; `null` only before that
 * (AC-402). [parlantiAttivi] feeds 'altri ▾' / 'cambia'; [unioni] the merge banners (AC-216);
 * [estrattiDisponibili] is `false` when the audio source is missing (AC-403: every '▶' disabled, with
 * [MESSAGGIO_ESTRATTI_NON_DISPONIBILI][snastro.ui.testi.MESSAGGIO_ESTRATTI_NON_DISPONIBILI]).
 */
data class PannelloVoci(
    val carte: List<CartaVoce>,
    val parlantiAttivi: List<ParlanteAttivo>,
    val unioni: List<PropostaDiUnione>,
    val estrattiDisponibili: Boolean,
    val unioneAbilitata: Boolean,
    /** ADR 0019 §6: 'Riassegna per somiglianza' in the header, `null` only before the first publish. */
    val somiglianza: PannelloSomiglianza? = null,
    /**
     * AC-I83/AC-I84 (ADR 0036): the cross-Parte banner, the second kind after [unioni] — `null` while a
     * Proposta di unione is shown (at most one banner), while S3 is read-only, or when no pair holds.
     */
    val traParti: CoppiaTraParti? = null,
    /** AC-I78: the OTHER Parti of the Incontro (`TrascrittoView.parti` minus this one); empty over one Parte. */
    val altreParti: Map<RegistrazioneId, Int> = emptyMap(),
) {
    /** AC-I78: the number of the Parte an extract comes from, when it is not the open one (`null` = no label). */
    fun parteDelloEstratto(estratto: EstrattoRef): Int? = altreParti[estratto.registrazioneId]
}

/**
 * A Voce as a target of 'Unisci con ▾' / 'Riassegna a ▾': its number and its label (Nome or "Voce n").
 * [parti] (AC-I77) are the Parti of the Incontro it speaks in, set only on a Voce that does not speak in the
 * open Parte ('parte n' in 'In altre parti'); [nome] is its attributed Nome (such a Voce has no card here).
 */
data class OpzioneVoce(
    val voceId: VoceId,
    val etichetta: String,
    val parti: List<Int> = emptyList(),
    val nome: String? = null,
)

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
    /** AC-I77: 'anche in parte 1, 3' — the OTHER Parti this Voce speaks in; empty = no line. */
    val altreParti: List<Int> = emptyList(),
    /** AC-I77: 'Unisci con ▾' → 'In altre parti': the Incontro's Voci absent from this Parte. */
    val vociAltreParti: List<OpzioneVoce> = emptyList(),
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
 * (INV-10); [destinazioni] are the OTHER Voci ('Riassegna a ▾', plus 'nuova voce'). [frase] (ADR 0019
 * §6) is 'Dai un nome a questa frase ▾' / 'Togli conferma', present only with exactly ONE Segmento
 * selected (AC-526/AC-528).
 */
data class BarraSelezione(
    val voceId: VoceId,
    val etichetta: String,
    val numeroSegmenti: Int,
    val dividiAbilitato: Boolean,
    val spiegazioneDividi: String?,
    val destinazioni: List<OpzioneVoce>,
    val abilitata: Boolean,
    val frase: MenuFrase? = null,
)

/**
 * AC-526/AC-528: the naming menu of the ONE selected [segmentoId] — the `attivo` [parlanti], then 'nuovo…';
 * [confermato] offers 'Togli conferma'. [abilitata] is `false` while read-only (AC-454), while a
 * similarity run is open (AC-531/AC-545) or while this Segmento's own naming is pending (AC-529).
 */
data class MenuFrase(
    val segmentoId: SegmentoId,
    val parlanti: List<ParlanteAttivo>,
    val confermato: Boolean,
    val abilitata: Boolean,
)
