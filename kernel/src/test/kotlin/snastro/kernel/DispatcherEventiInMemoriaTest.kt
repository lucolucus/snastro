package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
