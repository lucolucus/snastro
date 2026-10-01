package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * The ids the supplier minted for one Registrazione seeded by [AmbienteLettoreNomi.aggiungiRegistrazione] or
 * [AmbienteLettoreNomi.aggiungiParte]: its [id], the [incontroId] it is a Parte of, and the [voci] its Trascritto
 * added to that Incontro, in VoceId order.
 */
public data class RegistrazioneConiata(
    val id: RegistrazioneId,
    val incontroId: IncontroId,
    val voci: List<VoceRef>,
)
