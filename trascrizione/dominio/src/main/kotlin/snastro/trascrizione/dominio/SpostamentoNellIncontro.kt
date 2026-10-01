package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * One move of a [VociDellIncontro.riassegnaInBlocco] batch: [segmento] (any Parte of the Incontro) from [da] to [a].
 * [intervallo] is the Segmento's interval when the move was planned — the stale guard (ADR 0019 §4.5).
 */
public data class SpostamentoNellIncontro(
    val segmento: SegmentoRef,
    val da: VoceId,
    val a: VoceId,
    val intervallo: IntervalloMs,
)
