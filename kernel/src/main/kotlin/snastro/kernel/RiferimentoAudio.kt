package snastro.kernel

/** Opaque reference to an audio file, relative to the project folder (e.g. `audio/<registrazioneId>.mp3`). */
@JvmInline
public value class RiferimentoAudio(public val percorsoRelativo: String)
