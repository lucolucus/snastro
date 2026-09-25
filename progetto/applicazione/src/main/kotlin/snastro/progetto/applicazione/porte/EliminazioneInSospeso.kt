package snastro.progetto.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * The pending cleanup of a deleted Registrazione (ADR 0020 §4): its values AT deletion — the only way to locate
 * its files (`audio/`, `cache/audio/`, the Documento) once the Registrazione is gone. No biometric data.
 */
public data class EliminazioneInSospeso(
    val registrazioneId: RegistrazioneId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val riferimentoAudio: RiferimentoAudio,
)
