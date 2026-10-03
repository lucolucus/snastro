package snastro.progetto.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.Incontro

/**
 * Repository port of the [Incontro] aggregate (boundary `repo-incontro`, ADR 0033 §1). Every write joins the caller's
 * transaction. The Parti are the [snastro.progetto.dominio.Registrazione]s saved with this `incontroId` through
 * [RegistrazioneRepository]: an Incontro is saved before its first Parte and removed after its last (INV-I1).
 *
 * Method set closed by ADR 0033 (allow-list check); a new method is an ADR amendment.
 */
public interface IncontroRepository {
    /** The Incontro [id], or `null` if it was never saved or was removed. */
    public fun trova(id: IncontroId): Incontro?

    /**
     * Inserts [i]; saving it again changes nothing (its identity and Progetto are immutable, AC-I15). An Incontro
     * already saved under ANOTHER Progetto is REFUSED: the call throws [IllegalStateException] (a bug of the caller,
     * ADR 0003) and the stored Incontro stays as it was.
     */
    public fun salva(i: Incontro)

    /**
     * Deletes the Incontro [id]; an absent id is a no-op. REFUSED while a Parte still belongs to it: the call throws
     * (a bug of the caller, ADR 0003 — `EliminaRegistrazione` removes the last Parte first, ADR 0038 §2) and the
     * Incontro stays.
     */
    public fun rimuovi(id: IncontroId)

    /**
     * The Parti (Registrazioni) of the Incontro [id], UNORDERED: the order of the Parti is computed only by
     * `OrdineDelleParti` in `:progetto:dominio` (INV-I2), so no caller relies on this order. Empty for an unknown
     * Incontro.
     */
    public fun partiDi(id: IncontroId): List<RegistrazioneId>
}
