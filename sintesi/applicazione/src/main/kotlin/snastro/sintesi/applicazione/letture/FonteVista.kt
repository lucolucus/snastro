package snastro.sintesi.applicazione.letture

import snastro.kernel.RegistrazioneId

/**
 * AC-S104, INV-I13: one Fonte, with the CURRENT speaker and start time of its Segmento.
 * - [numeroParte] is `null` when the Parte [registrazioneId] is no longer in the Incontro;
 * - [segmentoPresente] is `false` when the Segmento no longer exists (its Parte was re-transcribed, ids are never
 *   reused): [inizioMs] is then `null` (Sintesi stores no interval) and so is [voce] (Sintesi stores no Voce per
 *   Fonte either), and the chip shows "parte n · non più presente".
 */
public data class FonteVista(
    val registrazioneId: RegistrazioneId,
    val numeroParte: Int?,
    val segmentoId: Int,
    val voce: VoceVista?,
    val inizioMs: Long?,
    val segmentoPresente: Boolean,
)
