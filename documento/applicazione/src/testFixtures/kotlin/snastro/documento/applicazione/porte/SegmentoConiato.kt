package snastro.documento.applicazione.porte

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** The ids the supplier minted for one seeded [SemeTurno]: its Segmento and the Voce it belongs to. */
public data class SegmentoConiato(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
)
