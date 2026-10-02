package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.OraDiInizio

/**
 * Command: replaces the OraDiInizio the user chose for [registrazioneId] (D-0009); `null` clears it. The time is
 * already validated: the caller builds it with `OraDiInizio.di` (INV-I14), which is strict `HH:mm:ss` with no trimming.
 * Mapping the user's text (blank → `null`, trim, zero-pad "9:05:00") is the caller's presenter's job, not this
 * command's nor the domain's.
 */
public data class ModificaOraDiInizio(
    public val registrazioneId: RegistrazioneId,
    public val ora: OraDiInizio?,
)
