package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * One move of a [Trascritto.riassegnaInBlocco] batch: [segmentoId] from [da] to [a]. [intervallo] is the
 * Segmento's interval when the move was planned — a stale guard, also against a Ritrascrivi generation swap
 * (ADR 0019 §4.5).
 */
public data class SpostamentoSegmento(
    val segmentoId: SegmentoId,
    val da: VoceId,
    val a: VoceId,
    val intervallo: IntervalloMs,
)
