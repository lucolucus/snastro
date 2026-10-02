package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import java.time.LocalTime

/**
 * The supplier side of [LettoreIncontroContratto]: the fake's map, or Progetto through ITS commands for the real
 * adapter (D2, dev-architecture-app.md#porta-contratto). It places every Parte in the supplier's order (INV-I2: date,
 * then OraDiInizio with an empty one last, then import order).
 */
public interface AmbienteLettoreIncontro {
    /** The implementation under contract, reading what has been seeded (and changed) so far. */
    public val lettore: LettoreIncontro

    /**
     * Capability (D-0037): the supplier can give an Incontro more than one Parte ([aggiungiParte]). The multi-Parte
     * cases of [LettoreIncontroContratto] are registered only when it is `true`; the real supplier switches it on when
     * the multi-file import into an Incontro (I2, `aggiungi-registrazione-incontro`) lands.
     */
    public val piuPartiPerIncontro: Boolean

    /** Imports one Registrazione dated [data] with [ora] as its OraDiInizio, the one Parte of a new Incontro. */
    public fun importa(data: LocalDate = DATA, ora: LocalTime? = null): RegistrazioneId

    /** Imports one Registrazione into the existing Incontro [incontroId]; only called when [piuPartiPerIncontro]. */
    public fun aggiungiParte(incontroId: IncontroId, data: LocalDate = DATA, ora: LocalTime? = null): RegistrazioneId

    /** The Incontro the supplier made [registrazioneId] a Parte of, at import. */
    public fun incontroDi(registrazioneId: RegistrazioneId): IncontroId

    /** ModificaDataRegistrazione of [registrazioneId]. */
    public fun modificaData(registrazioneId: RegistrazioneId, data: LocalDate)

    /** ModificaOraDiInizio of [registrazioneId] (`null` = unknown); only called when [piuPartiPerIncontro]. */
    public fun modificaOraDiInizio(registrazioneId: RegistrazioneId, ora: LocalTime?)

    /** Deletes the Registrazione [registrazioneId] in the supplier. */
    public fun elimina(registrazioneId: RegistrazioneId)

    public companion object {
        /** The date of a seed that does not name one. */
        public val DATA: LocalDate = LocalDate.of(2026, 10, 1)
    }
}
