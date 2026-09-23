package snastro.trascrizione.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId

/**
 * The supplier side of [LettoreRegistrazioneContratto]: one subclass per implementation seeds its
 * supplier (the fake's map, or Progetto through ITS commands for the real adapter — D2).
 */
public interface AmbienteLettoreRegistrazione {
    /** The Progetto every seeded Registrazione belongs to. */
    public val progettoId: ProgettoId

    /** The implementation under contract, reading what has been seeded. */
    public val lettore: LettoreRegistrazione

    /** Adds one Registrazione to [progettoId] and returns the id the supplier minted for it. */
    public fun semina(seme: SemeRegistrazione): RegistrazioneId
}
