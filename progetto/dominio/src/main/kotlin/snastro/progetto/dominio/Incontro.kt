package snastro.progetto.dominio

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId

/**
 * Aggregate root: a meeting of one [Progetto], whose Parti are its [Registrazione]s (ADR 0033 §1). It holds only its
 * identity and [progettoId], both immutable (AC-I15): title, date, numero della parte and "· N parti" are derived at
 * read time, the order of the Parti by [OrdineDelleParti]. The set rule of INV-I1 (never empty, created with its first
 * Parte, gone with its last) belongs to the commands that add and delete Parti.
 */
public class Incontro private constructor(public val id: IncontroId, public val progettoId: ProgettoId) {
    public companion object {
        /** A new or a persisted Incontro alike: with no other state there is nothing to re-validate. */
        public fun nuovo(id: IncontroId, progettoId: ProgettoId): Incontro = Incontro(id, progettoId)
    }
}
