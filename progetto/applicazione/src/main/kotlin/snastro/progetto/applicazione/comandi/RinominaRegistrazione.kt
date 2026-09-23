package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId

/** Command: renames the Registrazione [registrazioneId] to [nuovoTitolo] (AC-360/361). */
public data class RinominaRegistrazione(
    public val registrazioneId: RegistrazioneId,
    public val nuovoTitolo: String,
)
