package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
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

    /**
     * Capability flag (D-0037): `true` iff this supplier can give an Incontro a second Parte ([aggiungiParte]).
     * The real adapter switches it on when the I2 multi-file import lands; until then [LettoreRegistrazioneContratto]
     * registers its multi-Parte cases only where it is `true` — never a skipped test.
     */
    public val piuPartiPerIncontro: Boolean

    /**
     * Imports one more Registrazione INTO the existing Incontro [incontroId] (only when [piuPartiPerIncontro]) and
     * returns its minted id; the supplier places it in the Incontro's order ([INV-I2]).
     */
    public fun aggiungiParte(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId

    /** The Incontro the supplier made the seeded Registrazione [id] a Parte of, at import (ADR 0033 §4.1). */
    public fun incontroDi(id: RegistrazioneId): IncontroId

    /** Deletes the seeded Registrazione [id] in the supplier (its Incontro ceases with its only Parte). */
    public fun elimina(id: RegistrazioneId)

    /** Changes the DataRegistrazione of the seeded Registrazione [id] in the supplier. */
    public fun modificaData(id: RegistrazioneId, data: LocalDate)
}
