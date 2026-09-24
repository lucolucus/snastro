package snastro.ui.registrazione

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId

/** The key of a pending `ComandiVoce.nominaFrase`: the Segmento being named (ADR 0019 §5). */
data class FraseRef(val registrazioneId: RegistrazioneId, val segmentoId: SegmentoId)
