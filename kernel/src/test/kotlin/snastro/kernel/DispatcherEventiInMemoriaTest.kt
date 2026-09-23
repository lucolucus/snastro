package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DispatcherEventiInMemoriaTest : DispatcherEventiContratto() {
    private val effetti = EffettiInMemoria()
    private val reale = DispatcherEventiInMemoria(UnitaDiLavoroFinta(effetti))

    override fun ambiente(): Ambiente =
        object : Ambiente {
            override val dispatcher = reale
            override val unitaDiLavoro = reale.unitaDiLavoro

            override fun registraSincrono(abbonato: AbbonatoSincrono) = reale.registraSincrono(abbonato)

            override fun registraDopoCommit(abbonato: AbbonatoDopoCommit) = reale.registraDopoCommit(abbonato)

            override fun scrivi(effetto: String) = effetti.scrivi(effetto)

            override fun effetti() = effetti.visibili()
        }

    private object Evento : EventoPubblicato

    @Test
    fun `pubblicare fuori dalla transazione e un errore di programmazione`() {
        assertFailsWith<IllegalStateException> { reale.pubblica(Evento) }
    }

    @Test
    fun `un comando eseguito da un abbonato sincrono si unisce alla transazione e il suo Errore la annulla`() {
        reale.registraSincrono {
            reale.unitaDiLavoro.inTransazione<Unit> {
                effetti.scrivi("politica")
                Esito.Errore(ErroreDiProva.Fallito("politica"))
            }
        }
        reale.unitaDiLavoro.inTransazione {
            effetti.scrivi("comando")
            reale.pubblica(Evento)
            Esito.Ok(Unit)
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(emptySet(), effetti.visibili())
    }

    @Test
    fun `un abbonato dopo-commit puo aprire una nuova transazione e pubblicare`() {
        val ricevuti = mutableListOf<EventoPubblicato>()
        val seguito = object : EventoPubblicato {}
        reale.registraDopoCommit { e ->
            ricevuti += e
            if (e == Evento) {
                reale.unitaDiLavoro.inTransazione {
                    effetti.scrivi("rigenerato")
                    reale.pubblica(seguito)
                    Esito.Ok(Unit)
                }.atteso()
            }
        }
        reale.unitaDiLavoro.inTransazione {
            reale.pubblica(Evento)
            Esito.Ok(Unit)
        }.atteso()
        assertEquals(listOf(Evento, seguito), ricevuti)
        assertEquals(setOf("rigenerato"), effetti.visibili())
    }

    /** A delegate that dooms nothing: the dispatcher alone must carry the nested-failure rule. */
    private val ingenua = object : UnitaDiLavoro {
        override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> = blocco()
    }

    @Test
    fun `un eccezione annidata intercettata condanna il comando anche se la delegata non condanna`() {
        val dispatcher = DispatcherEventiInMemoria(ingenua)
        val ricevuti = mutableListOf<EventoPubblicato>()
        val guasto = IllegalArgumentException("annidato")
        dispatcher.registraDopoCommit { ricevuti += it }
        val lanciata = assertFailsWith<IllegalStateException> {
            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(Evento)
                assertFailsWith<IllegalArgumentException> {
                    dispatcher.unitaDiLavoro.inTransazione<Unit> { throw guasto }
                }
                Esito.Ok(Unit)
            }
        }
        assertSame(guasto, lanciata.cause)
        assertEquals(emptyList(), ricevuti)
    }

    @Test
    fun `un Errore annidato condanna il comando anche se la delegata non lo propaga`() {
        val dispatcher = DispatcherEventiInMemoria(ingenua)
        val ricevuti = mutableListOf<EventoPubblicato>()
        dispatcher.registraDopoCommit { ricevuti += it }
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(Evento)
            dispatcher.unitaDiLavoro.inTransazione<Unit> { Esito.Errore(ErroreDiProva.Fallito("annidato")) }
            Esito.Ok(Unit)
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(emptyList(), ricevuti)
    }
}
