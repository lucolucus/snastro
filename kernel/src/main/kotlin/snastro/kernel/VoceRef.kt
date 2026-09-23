package snastro.kernel

/** Cross-context correlation key of a `Voce`: the `Registrazione` it belongs to plus its [VoceId]. */
public data class VoceRef(val registrazioneId: RegistrazioneId, val voceId: VoceId)
