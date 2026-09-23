package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * The ids the supplier minted for one Registrazione seeded by [AmbienteLettoreNomi.aggiungiRegistrazione]:
 * its [id] and the [voci] of its Trascritto, in VoceId order.
 */
public data class RegistrazioneConiata(
    val id: RegistrazioneId,
    val voci: List<VoceRef>,
)
