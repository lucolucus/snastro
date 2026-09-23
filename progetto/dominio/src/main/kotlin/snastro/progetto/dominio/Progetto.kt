package snastro.progetto.dominio

import snastro.kernel.Creato
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza

/** Aggregate root: a Progetto scopes its Registrazioni and Parlanti. */
public class Progetto private constructor(
    public val id: ProgettoId,
    public val nome: NomeProgetto,
) {
    public companion object {
        public fun crea(id: ProgettoId, nome: NomeProgetto): Creato<Progetto, ProgettoCreato> =
            Creato(Progetto(id, nome), ProgettoCreato(id, nome.valore))

        /** Rebuilds a persisted Progetto; the database is trusted, nothing is re-validated (CR-15). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(id: ProgettoId, nome: String): Progetto = Progetto(id, NomeProgetto(nome))
    }
}
