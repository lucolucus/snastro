package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.OraDiInizio

/**
 * Command: replaces the OraDiInizio the user chose for [registrazioneId] (D-0009); `null` clears it. The time is
 * already validated: it is built by `OraDiInizio.di` before the command (INV-I14), so a blank text never arrives here.
 */
public data class ModificaOraDiInizio(
    public val registrazioneId: RegistrazioneId,
    public val ora: OraDiInizio?,
)
