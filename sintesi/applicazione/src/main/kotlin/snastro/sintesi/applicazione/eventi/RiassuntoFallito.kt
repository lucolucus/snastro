package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published Language (boundary `eventi-sintesi`): a Riassunto of the Registrazione ended `fallito`; [motivo] is the
 * canonical code of `MotivoFallimento` (e.g. `errore_modello`), mapped to Italian text only by `:ui`.
 */
public data class RiassuntoFallito(val registrazioneId: RegistrazioneId, val motivo: String) : EventoPubblicato
