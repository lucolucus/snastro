// Test convention for the Incontro of a one-Parte Registrazione.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.kernel

/**
 * The Incontro a test makes [registrazioneId] the one Parte of: deliberately NOT equal to it, so code that relied on
 * equal ids (a migration fact only, ADR 0034) fails its test. Also the id `seminaRegistrazioneDiProva` gives.
 */
public fun unIncontroDi(
    registrazioneId: RegistrazioneId,
): IncontroId = IncontroId("incontro-di-${registrazioneId.valore}")

/** The one Parte of the test Incontro [incontroId] built by [unIncontroDi]: its inverse, for test data only. */
public fun unicaParteDi(incontroId: IncontroId): RegistrazioneId =
    RegistrazioneId(incontroId.valore.removePrefix("incontro-di-"))

/** The one Parte of the Incontro of [voceRef], under the [unIncontroDi] convention (test data only). */
public fun unicaParteDi(voceRef: VoceRef): RegistrazioneId = unicaParteDi(voceRef.incontroId)
