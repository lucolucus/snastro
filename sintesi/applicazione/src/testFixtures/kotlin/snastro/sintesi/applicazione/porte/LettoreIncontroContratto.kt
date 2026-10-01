package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IncontroId
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [LettoreIncontro] (boundary `lettore-incontro-sintesi`, ADR 0033 §4.1, AC-I204): one
 * subclass per implementation — [LettoreIncontroFinta] (D1) and `LettoreIncontroDaProgetto` (D2). No order is asserted:
 * the Parti are unordered until wave 4.
 */
public abstract class LettoreIncontroContratto {
    /** A fresh supplier: one Progetto, no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreIncontro

    @Test
    public fun `AC-I204 parti restituisce l'insieme delle Registrazioni dell'Incontro`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.importa()

        assertEquals(setOf(r), lettore.parti(a.incontroDi(r))?.toSet())
    }

    @Test
    public fun `AC-I204 parti di un Incontro sconosciuto restituisce null`() {
        val a = ambiente()
        a.importa()

        assertNull(a.lettore.parti(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I204 parti dopo l'eliminazione dell'unica Parte restituisce null`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.importa()
        val incontro = a.incontroDi(r)

        a.elimina(r)

        assertNull(lettore.parti(incontro))
    }

    @Test
    public fun `AC-I204 parti non elenca mai una Registrazione di un altro Incontro`() {
        val a = ambiente()
        val prima = a.importa()
        val seconda = a.importa()

        assertEquals(listOf(prima), a.lettore.parti(a.incontroDi(prima)))
        assertEquals(listOf(seconda), a.lettore.parti(a.incontroDi(seconda)))
    }
}
