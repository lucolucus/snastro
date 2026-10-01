package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/** Trascrizione's own copy of one Parte of an Incontro (boundary `parti-per-trascrizione`): [numero] is 1..N. */
public data class ParteDiIncontro(val registrazioneId: RegistrazioneId, val numero: Int)
