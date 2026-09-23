package snastro.trascrizione.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * The supplier side of [LettoreRegistrazioneContratto]: one subclass per implementation seeds its
 * supplier (the fake's map, or Progetto through ITS commands for the real adapter — D2).
 */
public interface AmbienteLettoreRegistrazione {
    /** The Progetto every seeded Registrazione belongs to. */
    public val progettoId: ProgettoId

    /** The implementation under contract, reading what has been seeded (and changed) so far. */
    public val lettore: LettoreRegistrazione

    /** Adds one Registrazione to [progettoId] and returns the id the supplier minted for it. */
    public fun semina(seme: SemeRegistrazione): RegistrazioneId

    /** Changes the DataRegistrazione of the seeded Registrazione [id] in the supplier. */
    public fun modificaData(id: RegistrazioneId, data: LocalDate)
}
