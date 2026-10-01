package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Sintesi reads the Parti of an Incontro from Progetto (boundary
 * `lettore-incontro-sintesi`, ADR 0033 §4.1, D-0031; Published Language only). Widened in wave 4 to ordered, numbered
 * Parti. Contract: `LettoreIncontroContratto`.
 */
public interface LettoreIncontro {
    /**
     * The Parti (Registrazioni) of the Incontro [incontroId], UNORDERED: no caller sorts this list nor relies on its
     * order. `null` for an unknown Incontro, or one that ceased with its last Parte; a known Incontro has ≥ 1 Parte.
     */
    public fun parti(incontroId: IncontroId): List<RegistrazioneId>?
}

/**
 * The one Parte of [incontroId], `null` when the Incontro is unknown or ceased. TRANSITION (ADR 0033 §6, D-0023): every
 * Incontro has exactly one Parte until the multi-file import (I2), which lands after the ordered reads of wave 4; a
 * second Parte here is a programmer error, never silently ignored.
 */
internal fun LettoreIncontro.parteUnica(incontroId: IncontroId): RegistrazioneId? =
    parti(incontroId)?.let { parti ->
        check(parti.size == 1) { "Incontro $incontroId con ${parti.size} Parti: le letture ordinate sono del wave 4" }
        parti.single()
    }
