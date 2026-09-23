package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherEventiFintaTest : DispatcherEventiContratto() {
    private val effetti = EffettiInMemoria()
    private val finta = DispatcherEventiFinta(UnitaDiLavoroFinta(effetti))

    override fun ambiente(): Ambiente =
        object : Ambiente {
            override val dispatcher = finta
            override val unitaDiLavoro = finta.unitaDiLavoro

            override fun registraSincrono(abbonato: AbbonatoSincrono) = finta.registraSincrono(abbonato)

            override fun registraDopoCommit(abbonato: AbbonatoDopoCommit) = finta.registraDopoCommit(abbonato)

            override fun scrivi(effetto: String) = effetti.scrivi(effetto)

            override fun effetti() = effetti.visibili()
        }

    private data class Evento(val n: Int) : EventoPubblicato

    @Test
    fun `pubblicati registra solo gli eventi delle transazioni confermate`() {
        finta.unitaDiLavoro.inTransazione {
            finta.pubblica(Evento(1))
            Esito.Ok(Unit)
        }.atteso()
        finta.unitaDiLavoro.inTransazione<Unit> {
            finta.pubblica(Evento(2))
            Esito.Errore(ErroreDiProva.Fallito("no"))
        }
        assertEquals(listOf<EventoPubblicato>(Evento(1)), finta.pubblicati)
    }
}
