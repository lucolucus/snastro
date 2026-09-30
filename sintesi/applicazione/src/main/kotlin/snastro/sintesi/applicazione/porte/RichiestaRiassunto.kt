package snastro.sintesi.applicazione.porte

/**
 * What Sintesi sends to [ModelloLinguistico] (ADR 0021 §4).
 * @property ingresso the labelled input built by `IngressoRiassunto`: lines `[s<segmentoId> V<voceId>] <testo>`
 *   (no m:ss, ADR 0021 §4 amended 2026-09-26) plus the legend `V<n> = Voce n` (never a Nome, ADR 0032).
 * @property argomento the optional topic, already validated by the `Argomento` VO.
 * @property lunghezzaMassimaParole the word cap fixed on the Riassunto ([INV-S10]).
 */
public data class RichiestaRiassunto(
    val ingresso: String,
    val argomento: String?,
    val lunghezzaMassimaParole: Int,
)
