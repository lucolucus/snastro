package snastro.progetto.applicazione.porte

import snastro.progetto.dominio.Progetto

/** Repository port of the [Progetto] aggregate: one Progetto per project database. */
public interface ProgettoRepository {
    /** The Progetto of this database, or null if it has not been saved yet. */
    public fun trova(): Progetto?

    /** Saves [p] inside the caller's transaction. */
    public fun salva(p: Progetto)
}
