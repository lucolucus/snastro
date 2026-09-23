package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Contract of [UnitaDiLavoro] (ADR 0012): commit on [Esito.Ok], rollback on [Esito.Errore] or on a
 * thrown exception, nested calls join the outer transaction. One subclass per implementation
 * (`UnitaDiLavoroFinta` here, `UnitaDiLavoroSql` in `:persistenza`).
 */
public abstract class UnitaDiLavoroContratto {
    /** A fresh, empty environment: the unit of work under test plus a transactional effect it governs. */
    public interface Ambiente {
        public val unitaDiLavoro: UnitaDiLavoro

        /** Writes one effect; called inside `inTransazione`. */
        public fun scrivi(effetto: String)

        /** The committed (visible) effects. */
        public fun effetti(): Set<String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-2 un blocco Ok conserva i suoi effetti e ne restituisce il valore`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione {
            a.scrivi("uno")
            a.scrivi("due")
            Esito.Ok(VALORE)
        }
        assertEquals(VALORE, esito.atteso())
        assertEquals(setOf("uno", "due"), a.effetti())
    }

    @Test
    public fun `AC-2 un blocco che restituisce Errore non lascia effetti e restituisce l Errore`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione<Int> {
            a.scrivi("uno")
            Esito.Errore(ERRORE)
        }
        assertEquals(ERRORE, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 un blocco che lancia non lascia effetti e l eccezione si propaga`() {
        val a = ambiente()
        assertFailsWith<GuastoDiProva> {
            a.unitaDiLavoro.inTransazione<Int> {
                a.scrivi("uno")
                throw GuastoDiProva()
            }
        }
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 un rollback non tocca gli effetti delle transazioni gia confermate`() {
        val a = ambiente()
        a.unitaDiLavoro.inTransazione {
            a.scrivi("confermato")
            Esito.Ok(Unit)
        }
        a.unitaDiLavoro.inTransazione<Unit> {
            a.scrivi("annullato")
            Esito.Errore(ERRORE)
        }
        assertEquals(setOf("confermato"), a.effetti())
    }

    @Test
    public fun `AC-2 una transazione annidata partecipa a quella esterna e ne segue il rollback`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione<Unit> {
            a.scrivi("esterno")
            a.unitaDiLavoro.inTransazione {
                a.scrivi("interno")
                Esito.Ok(Unit)
            }
            Esito.Errore(ERRORE)
        }
        esito.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 una transazione annidata confermata con l esterna conserva i suoi effetti`() {
        val a = ambiente()
        a.unitaDiLavoro.inTransazione {
            a.scrivi("esterno")
            a.unitaDiLavoro.inTransazione {
                a.scrivi("interno")
                Esito.Ok(Unit)
            }
        }.atteso()
        assertEquals(setOf("esterno", "interno"), a.effetti())
    }

    private class GuastoDiProva : IllegalStateException("guasto di prova")

    private companion object {
        const val VALORE = 42
        val ERRORE = ErroreDiProva.Fallito("rifiutato")
    }
}
