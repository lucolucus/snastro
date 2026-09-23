package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Contract of [DispatcherEventi] (ADR 0012): synchronous subscribers run in publication order
 * inside the transaction and an [Esito.Errore] of theirs rolls the command back; after-commit
 * subscribers run only after commit, never after a rollback. One subclass per implementation.
 */
public abstract class DispatcherEventiContratto {
    /** A fresh environment: dispatcher, the unit of work services receive, a transactional effect. */
    public interface Ambiente {
        public val dispatcher: DispatcherEventi
        public val unitaDiLavoro: UnitaDiLavoro

        public fun registraSincrono(abbonato: AbbonatoSincrono)

        public fun registraDopoCommit(abbonato: AbbonatoDopoCommit)

        /** Writes one effect; called inside `inTransazione`. */
        public fun scrivi(effetto: String)

        /** The committed (visible) effects. */
        public fun effetti(): Set<String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-3 gli abbonati sincroni girano nell ordine di pubblicazione dentro la transazione`() {
        val a = ambiente()
        val ricevuti = mutableListOf<String>()
        a.registraSincrono { e ->
            ricevuti += "s1-${(e as EventoDiProva).n}"
            Esito.Ok(Unit)
        }
        a.registraSincrono { e ->
            ricevuti += "s2-${(e as EventoDiProva).n}"
            Esito.Ok(Unit)
        }
        a.unitaDiLavoro.inTransazione {
            a.dispatcher.pubblica(EventoDiProva(1))
            assertEquals(listOf("s1-1", "s2-1"), ricevuti)
            a.dispatcher.pubblica(EventoDiProva(2))
            Esito.Ok(Unit)
        }.atteso()
        assertEquals(listOf("s1-1", "s2-1", "s1-2", "s2-2"), ricevuti)
    }

    @Test
    public fun `AC-3 gli effetti di un abbonato sincrono seguono il rollback del comando`() {
        val a = ambiente()
        a.registraSincrono { e ->
            a.scrivi("abbonato-${(e as EventoDiProva).n}")
            Esito.Ok(Unit)
        }
        a.unitaDiLavoro.inTransazione<Unit> {
            a.dispatcher.pubblica(EventoDiProva(1))
            Esito.Errore(ERRORE)
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(emptySet(), a.effetti())
    }

    @Test
    public fun `AC-3 un Errore di un abbonato sincrono annulla il comando`() {
        val a = ambiente()
        val dopoCommit = mutableListOf<EventoPubblicato>()
        a.registraSincrono { Esito.Errore(ERRORE) }
        a.registraDopoCommit { dopoCommit += it }
        val esito = a.unitaDiLavoro.inTransazione {
            a.scrivi("comando")
            a.dispatcher.pubblica(EventoDiProva(1))
            Esito.Ok(Unit)
        }
        assertEquals(ERRORE, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
        assertEquals(emptyList(), dopoCommit)
    }

    @Test
    public fun `AC-3 gli abbonati dopo-commit girano solo dopo il commit nell ordine di pubblicazione`() {
        val a = ambiente()
        val ricevuti = mutableListOf<EventoPubblicato>()
        val effettiVisti = mutableListOf<Set<String>>()
        a.registraDopoCommit {
            ricevuti += it
            effettiVisti += a.effetti()
        }
        a.unitaDiLavoro.inTransazione {
            a.scrivi("comando")
            a.dispatcher.pubblica(EventoDiProva(1))
            a.dispatcher.pubblica(EventoDiProva(2))
            assertEquals(emptyList(), ricevuti)
            Esito.Ok(Unit)
        }.atteso()
        assertEquals(listOf<EventoPubblicato>(EventoDiProva(1), EventoDiProva(2)), ricevuti)
        assertEquals(listOf(setOf("comando"), setOf("comando")), effettiVisti)
    }

    @Test
    public fun `AC-3 gli abbonati dopo-commit non girano dopo un rollback per Errore`() {
        val a = ambiente()
        val ricevuti = mutableListOf<EventoPubblicato>()
        a.registraDopoCommit { ricevuti += it }
        a.unitaDiLavoro.inTransazione<Unit> {
            a.dispatcher.pubblica(EventoDiProva(1))
            Esito.Errore(ERRORE)
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(emptyList(), ricevuti)
    }

    @Test
    public fun `AC-3 gli abbonati dopo-commit non girano dopo un rollback per eccezione`() {
        val a = ambiente()
        val ricevuti = mutableListOf<EventoPubblicato>()
        a.registraDopoCommit { ricevuti += it }
        assertFailsWith<GuastoDiProva> {
            a.unitaDiLavoro.inTransazione<Unit> {
                a.dispatcher.pubblica(EventoDiProva(1))
                throw GuastoDiProva()
            }
        }
        assertEquals(emptyList(), ricevuti)
        a.unitaDiLavoro.inTransazione { Esito.Ok(Unit) }.atteso()
        assertEquals(emptyList(), ricevuti, "gli eventi annullati non trapelano nella transazione successiva")
    }

    @Test
    public fun `AC-3 un Errore di una transazione annidata annulla il comando e gli abbonati dopo-commit`() {
        val a = ambiente()
        val ricevuti = mutableListOf<EventoPubblicato>()
        a.registraDopoCommit { ricevuti += it }
        val esito = a.unitaDiLavoro.inTransazione {
            a.scrivi("comando")
            a.dispatcher.pubblica(EventoDiProva(1))
            a.unitaDiLavoro.inTransazione<Unit> { Esito.Errore(ERRORE) }
            Esito.Ok(Unit)
        }
        assertEquals(ERRORE, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(emptySet(), a.effetti())
        assertEquals(emptyList(), ricevuti)
    }

    @Test
    public fun `AC-3 un abbonato dopo-commit che lancia non impedisce la consegna agli altri`() {
        val a = ambiente()
        val ricevuti = mutableListOf<String>()
        val primo = GuastoDiProva()
        val secondo = GuastoDiProva()
        a.registraDopoCommit { throw primo }
        a.registraDopoCommit { ricevuti += "sano-${(it as EventoDiProva).n}" }
        a.registraDopoCommit { if ((it as EventoDiProva).n == 2) throw secondo }
        val lanciata = assertFailsWith<GuastoDiProva> {
            a.unitaDiLavoro.inTransazione {
                a.scrivi("comando")
                a.dispatcher.pubblica(EventoDiProva(1))
                a.dispatcher.pubblica(EventoDiProva(2))
                Esito.Ok(Unit)
            }
        }
        assertSame(primo, lanciata)
        assertEquals(listOf<Throwable>(secondo), lanciata.suppressed.toList())
        assertEquals(listOf("sano-1", "sano-2"), ricevuti)
        assertEquals(setOf("comando"), a.effetti(), "il comando resta confermato")
    }

    private data class EventoDiProva(val n: Int) : EventoPubblicato

    private class GuastoDiProva : RuntimeException("guasto di prova")

    private companion object {
        val ERRORE = ErroreDiProva.Fallito("politica violata")
    }
}
