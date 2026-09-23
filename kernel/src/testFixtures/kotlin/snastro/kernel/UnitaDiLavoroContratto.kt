package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Contract of [UnitaDiLavoro] (ADR 0012): commit on [Esito.Ok], rollback on [Esito.Errore] or on a
 * thrown exception, nested calls join the outer transaction and a nested failure dooms it (the outer
 * block's own Errore wins; a swallowed nested exception surfaces as the `cause`). One subclass per implementation
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

    @Test
    public fun `AC-2 un Errore annidato condanna la transazione esterna anche se questa restituisce Ok`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione {
            a.scrivi("esterno")
            a.unitaDiLavoro.inTransazione<Unit> {
                a.scrivi("interno")
                Esito.Errore(ERRORE)
            }
            a.scrivi("dopo")
            Esito.Ok(VALORE)
        }
        assertEquals(ERRORE, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 un eccezione annidata si propaga e annulla la transazione esterna`() {
        val a = ambiente()
        assertFailsWith<GuastoDiProva> {
            a.unitaDiLavoro.inTransazione {
                a.scrivi("esterno")
                a.unitaDiLavoro.inTransazione<Unit> {
                    a.scrivi("interno")
                    throw GuastoDiProva()
                }
            }
        }
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 un eccezione annidata intercettata dall esterna annulla comunque tutto e non riporta Ok`() {
        val a = ambiente()
        val guasto = GuastoDiProva()
        val lanciata = assertFailsWith<IllegalStateException> {
            a.unitaDiLavoro.inTransazione {
                a.scrivi("esterno")
                assertFailsWith<GuastoDiProva> {
                    a.unitaDiLavoro.inTransazione<Unit> {
                        a.scrivi("interno")
                        throw guasto
                    }
                }
                Esito.Ok(VALORE)
            }
        }
        assertSame(guasto, lanciata.cause, "la causa annidata non si perde")
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 un eccezione annidata tradotta in Errore dall esterna annulla tutto e restituisce quell Errore`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione<Int> {
            a.scrivi("esterno")
            try {
                a.unitaDiLavoro.inTransazione<Unit> {
                    a.scrivi("interno")
                    throw GuastoDiProva()
                }
            } catch (e: GuastoDiProva) {
                return@inTransazione Esito.Errore(ErroreDiProva.Fallito("tradotto: ${e.message}"))
            }
            Esito.Ok(VALORE)
        }
        assertEquals(TRADOTTO, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 l Errore restituito dall esterna prevale su un Errore annidato`() {
        val a = ambiente()
        val esito = a.unitaDiLavoro.inTransazione<Int> {
            a.scrivi("esterno")
            a.unitaDiLavoro.inTransazione<Unit> { Esito.Errore(ERRORE) }
            Esito.Errore(TRADOTTO)
        }
        assertEquals(TRADOTTO, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-2 dopo una transazione condannata la successiva riparte pulita`() {
        val a = ambiente()
        a.unitaDiLavoro.inTransazione {
            a.unitaDiLavoro.inTransazione<Unit> { Esito.Errore(ERRORE) }
            Esito.Ok(Unit)
        }.erroreAtteso<ErroreDiProva.Fallito>()
        a.unitaDiLavoro.inTransazione {
            a.scrivi("dopo")
            Esito.Ok(Unit)
        }.atteso()
        assertEquals(setOf("dopo"), a.effetti())
    }

    private class GuastoDiProva : RuntimeException("guasto di prova")

    private companion object {
        const val VALORE = 42
        val ERRORE = ErroreDiProva.Fallito("rifiutato")
        val TRADOTTO = ErroreDiProva.Fallito("tradotto: guasto di prova")
    }
}
