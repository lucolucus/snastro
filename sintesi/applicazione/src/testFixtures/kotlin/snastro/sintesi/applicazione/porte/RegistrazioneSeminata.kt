package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/** One seeded Registrazione: its [id], the [incontroId] it is a Parte of, and the [voci] it brought, in VoceId order. */
public data class RegistrazioneSeminata(val id: RegistrazioneId, val incontroId: IncontroId, val voci: List<VoceRef>)
