package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Contract of [LetturaCoerente] together with the [UnitaDiLavoro] backed by the SAME per-thread state
 * (ADR 0029 §2, cases 1–7 of §4). Separate from `UnitaDiLavoroContratto`, which stays the contract of
 * its own port. Subclasses: `UnitaDiLavoroFinta` (`:kernel`), `UnitaDiLavoroSql` and `UnitaDiLavoroSql`
 * behind `DispatcherEventiInMemoria.unitaDiLavoro` (`:persistenza`).
 */
public abstract class LetturaCoerenteContratto {
    /** A fresh, empty environment: both ports over one state, plus a transactional effect they govern. */
    public interface Ambiente {
        public val lettura: LetturaCoerente

        /** Backed by the same state as [lettura]. */
        public val unitaDiLavoro: UnitaDiLavoro

        /**
         * Writes one effect; must fail (leaving no effect) inside an outermost [LetturaCoerente.inLettura].
         *
         * B18: this is THIS ENVIRONMENT's OWN plumbing (each subclass's `ambiente()` wires its own
         * `check(!letturaAperta)` or the SQL adapter's `query_only`) — it is not proof that [UnitaDiLavoroFinta]
         * enforces the rule on anyone's behalf. [UnitaDiLavoroFinta.letturaAperta] only TRACKS whether a read is
         * open; it refuses nothing by itself. A real consumer repository fake (`ParlanteRepositoryFinta`,
         * `TrascrittoRepositoryFinta`, `RiassuntoRepositoryFinta`, …) that omits its OWN
         * `check(!lettura.letturaAperta)` before writing would still pass THIS contract while silently
         * diverging from the SQL adapter's `query_only` — a write-during-read application bug would then go
         * undetected on every D1 (Finta-backed) test and surface only on the real SQL adapter. Authoring a
         * repository fake means adding this check yourself; it is not inherited for free.
         */
        public fun scrivi(effetto: String)

        /** The committed effects, read outside any unit. */
        public fun effetti(): List<String>

        /** The effects visible through [lettura] (joins an enclosing unit, if any). */
        public fun leggi(): Set<String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-C15 inLettura restituisce il valore del blocco e vede lo stato confermato`() {
        val a = ambiente()
        a.confermati("uno", "due")

        val letto = a.lettura.inLettura { a.leggi() to VALORE }

        assertEquals(setOf("uno", "due") to VALORE, letto)
    }

    @Test
    public fun `AC-C16 inLettura dentro inTransazione vi partecipa e vede la scrittura non confermata`() {
        val a = ambiente()
        val visti = a.unitaDiLavoro.inTransazione {
            a.scrivi("prima")
            val visti = a.lettura.inLettura { a.leggi() }
            a.scrivi("dopo")
            Esito.Ok(visti)
        }.atteso()

        assertEquals(setOf("prima"), visti)
        assertEquals(
            listOf("dopo", "prima"),
            a.effetti().sorted(),
            "la lettura annidata non rende la transazione di sola lettura",
        )
    }

    @Test
    public fun `AC-C17 inLettura dentro inLettura vi partecipa e resta di sola lettura`() {
        val a = ambiente()
        a.confermati("uno")

        val (esterno, interno) = a.lettura.inLettura {
            val interno = a.lettura.inLettura { a.leggi() }
            assertFails("dopo la lettura annidata l'esterna e ancora di sola lettura") { a.scrivi("vietato") }
            a.leggi() to interno
        }

        assertEquals(setOf("uno"), esterno)
        assertEquals(esterno, interno)
        assertEquals(listOf("uno"), a.effetti())
    }

    @Test
    public fun `AC-C18 inTransazione dentro inLettura lancia IllegalStateException senza eseguire il blocco`() {
        val a = ambiente()
        a.confermati("uno")
        var entrato = false

        assertFailsWith<IllegalStateException> {
            a.lettura.inLettura {
                a.unitaDiLavoro.inTransazione {
                    entrato = true
                    a.scrivi("vietato")
                    Esito.Ok(Unit)
                }
            }
        }

        assertFalse(entrato, "il blocco della transazione non deve partire")
        assertEquals(listOf("uno"), a.effetti())
    }

    @Test
    public fun `AC-C19 una scrittura dentro inLettura fallisce e non lascia effetti`() {
        val a = ambiente()
        a.confermati("uno")

        assertFails { a.lettura.inLettura { a.scrivi("vietato") } }

        assertEquals(listOf("uno"), a.effetti())
    }

    @Test
    public fun `AC-C20 un eccezione in inLettura si propaga e la transazione successiva conferma e scrive`() {
        val a = ambiente()
        val guasto = GuastoDiProva()

        val lanciata = assertFailsWith<GuastoDiProva> { a.lettura.inLettura { throw guasto } }
        a.unitaDiLavoro.inTransazione {
            a.scrivi("dopo")
            Esito.Ok(Unit)
        }.atteso()

        assertSame(guasto, lanciata)
        assertEquals(listOf("dopo"), a.effetti())
        assertEquals(setOf("dopo"), a.leggi())
    }

    @Test
    public fun `AC-C20 dopo una scrittura rifiutata in inLettura la transazione successiva non e di sola lettura`() {
        val a = ambiente()

        assertFails { a.lettura.inLettura { a.scrivi("vietato") } }
        a.confermati("dopo")

        assertEquals(listOf("dopo"), a.effetti())
    }

    /**
     * B22: rule 2 ("`inLettura` nested in `inTransazione` JOINS it... opens nothing") means [modo] never
     * becomes `LETTURA` here, so this further `inTransazione` does NOT hit rule 4's guard (AC-C18, an
     * OUTERMOST `inLettura`) — it is accepted like any other nested write. Pins the current, documented
     * behaviour (no ADR 0029 §2.4 line names this combination unconditionally forbidden either way).
     */
    @Test
    public fun `B22 inTransazione dentro inLettura annidata in inTransazione e accettato`() {
        val a = ambiente()

        val esito = a.unitaDiLavoro.inTransazione {
            a.scrivi("esterno")
            val interno = a.lettura.inLettura {
                a.unitaDiLavoro.inTransazione {
                    a.scrivi("annidato")
                    Esito.Ok(Unit)
                }
            }
            interno.poi { Esito.Ok(Unit) }
        }

        assertIs<Esito.Ok<Unit>>(esito)
        assertEquals(listOf("annidato", "esterno"), a.effetti().sorted())
    }

    @Test
    public fun `AC-C21 un eccezione in inLettura annidata condanna la transazione anche se l esterna restituisce Ok`() {
        val a = ambiente()
        val guasto = GuastoDiProva()

        val lanciata = assertFailsWith<IllegalStateException> {
            a.unitaDiLavoro.inTransazione {
                a.scrivi("esterno")
                assertFailsWith<GuastoDiProva> { a.lettura.inLettura<Unit> { throw guasto } }
                a.scrivi("dopo")
                Esito.Ok(VALORE)
            }
        }

        assertSame(guasto, lanciata.cause, "la causa annidata non si perde")
        assertEquals(emptyList(), a.effetti())
    }

    private fun Ambiente.confermati(vararg effetti: String) {
        unitaDiLavoro.inTransazione {
            effetti.forEach(::scrivi)
            Esito.Ok(Unit)
        }.atteso()
    }

    private class GuastoDiProva : RuntimeException("guasto di prova")

    private companion object {
        const val VALORE = 42
    }
}
