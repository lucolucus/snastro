package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/** Command: replaces the DataRegistrazione the user chose for [registrazioneId] (AC-62). */
public data class ModificaDataRegistrazione(
    public val registrazioneId: RegistrazioneId,
    public val nuovaData: LocalDate,
)
