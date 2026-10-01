package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * The supplier side of [LettoreIncontroContratto]: the fake's map, or Progetto through ITS commands for the real
 * adapter (D2, dev-architecture-app.md#porta-contratto).
 */
public interface AmbienteLettoreIncontro {
    /** The implementation under contract, reading what has been seeded (and deleted) so far. */
    public val lettore: LettoreIncontro

    /** Imports one Registrazione, the one Parte of a new Incontro, and returns its id. */
    public fun importa(): RegistrazioneId

    /** The Incontro the supplier made [registrazioneId] a Parte of, at import. */
    public fun incontroDi(registrazioneId: RegistrazioneId): IncontroId

    /** Deletes the Registrazione [registrazioneId] in the supplier. */
    public fun elimina(registrazioneId: RegistrazioneId)
}
