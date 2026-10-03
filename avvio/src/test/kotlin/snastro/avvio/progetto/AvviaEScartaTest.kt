package snastro.avvio.progetto

import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private val REG = RegistrazioneId("id-1")

/** L270 (D-0062): 'Trascrivi' committed with a failed after-commit follow-up still drops the similarity. */
class AvviaEScartaTest {
    private val scartate = mutableListOf<RegistrazioneId>()

    @Test
    fun `L270 un avvio confermato con un abbonato dopo-commit fallito scarta la somiglianza e rilancia la consegna`() {
        val consegna = ConsegnaDopoCommitFallita(IllegalStateException("abbonato fallito"))
        val avvia = avviaEScarta({ throw consegna }) { scartate += it }

        val lanciata = assertFailsWith<ConsegnaDopoCommitFallita> { avvia(AvviaElaborazione(REG)) }

        assertSame(consegna, lanciata, "S2 la riconosce come confermata")
        assertEquals(listOf(REG), scartate)
    }

    @Test
    fun `L270 un avvio fallito prima del commit non scarta nulla, un avvio Ok scarta`() {
        assertFailsWith<IllegalStateException> {
            avviaEScarta({ error("guasto SQL") }) { scartate += it }(AvviaElaborazione(REG))
        }
        val ok = avviaEScarta({ Esito.Ok(Unit) }) { scartate += it }(AvviaElaborazione(REG))

        assertEquals(Esito.Ok(Unit), ok)
        assertEquals(listOf(REG), scartate, "solo l Ok scarta")
    }
}
