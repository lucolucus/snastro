package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract of [CartelleProgetti] (R0, EX-7/EX-12): one subclass per implementation (`CartelleProgettiFinta` here, the
 * file-system one in `:progetto:adattatori`), each saying how a project folder is made, moved away and put back.
 */
public abstract class CartelleProgettiContratto {
    protected abstract fun cartelle(): CartelleProgetti

    /** A project folder that exists now; its path. */
    protected abstract fun unaCartella(): String

    /** The folder [percorso] is moved elsewhere. */
    protected abstract fun spostaVia(percorso: String)

    /** The folder [percorso], moved by [spostaVia], is put back. */
    protected abstract fun rimetti(percorso: String)

    @Test
    public fun `R0 una cartella presente esiste, spostata altrove non esiste piu`() {
        val cartelle = cartelle()
        val percorso = unaCartella()
        assertTrue(cartelle.esiste(percorso))

        spostaVia(percorso)

        assertFalse(cartelle.esiste(percorso))
    }

    @Test
    public fun `R0 una cartella rimessa al suo posto esiste di nuovo`() {
        val cartelle = cartelle()
        val percorso = unaCartella()
        spostaVia(percorso)
        assertFalse(cartelle.esiste(percorso))

        rimetti(percorso)

        assertTrue(cartelle.esiste(percorso))
    }
}
