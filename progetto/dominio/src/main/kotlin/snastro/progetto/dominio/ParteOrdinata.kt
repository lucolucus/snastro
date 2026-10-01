package snastro.progetto.dominio

import snastro.kernel.RegistrazioneId

/** A Parte at its place in the Incontro: [numero] is its 1-based rank in [OrdineDelleParti] (INV-I2). */
public data class ParteOrdinata(val registrazioneId: RegistrazioneId, val numero: Int)
