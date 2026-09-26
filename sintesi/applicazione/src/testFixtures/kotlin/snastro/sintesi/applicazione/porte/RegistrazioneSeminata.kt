package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/** One seeded Registrazione: its [id] and the [voci] of its Trascritto, in VoceId order. */
public data class RegistrazioneSeminata(val id: RegistrazioneId, val voci: List<VoceRef>)
