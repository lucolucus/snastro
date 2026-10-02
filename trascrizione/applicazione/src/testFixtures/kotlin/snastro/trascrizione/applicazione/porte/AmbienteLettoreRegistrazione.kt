package snastro.trascrizione.applicazione.porte

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

    /** The Incontro the supplier made the seeded Registrazione [id] a Parte of, at import (ADR 0033 §4.1). */
    public fun incontroDi(id: RegistrazioneId): IncontroId

    /** Changes the DataRegistrazione of the seeded Registrazione [id] in the supplier. */
    public fun modificaData(id: RegistrazioneId, data: LocalDate)

    /**
     * Environment capability (D-0037): the supplier can give an Incontro more than one Parte (Progetto: once the I2
     * multi-file import exists). Without it [seminaIncontro] is never called.
     */
    public val piuPartiPerIncontro: Boolean

    /** Adds the Registrazioni of [semi] as the Parti of ONE new Incontro of [progettoId] and returns that Incontro. */
    public fun seminaIncontro(semi: List<SemeRegistrazione>): IncontroId

    /** The supplier's OWN order of the Parti of [incontroId] (INV-I2, decided by Progetto, never by the reader). */
    public fun ordineDelleParti(incontroId: IncontroId): List<RegistrazioneId>
}
