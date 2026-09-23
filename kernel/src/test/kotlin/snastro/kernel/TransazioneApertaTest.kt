package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TransazioneApertaTest {
    private val uow = UnitaDiLavoroFinta()

    @Test
    fun `AC-266 transazioneAperta e falsa fuori e vera dentro il blocco e di nuovo falsa dopo il commit`() {
        assertFalse(uow.transazioneAperta)
        val dentro = uow.inTransazione { Esito.Ok(uow.transazioneAperta) }.atteso()
        assertEquals(true, dentro)
        assertFalse(uow.transazioneAperta)
    }

    @Test
    fun `AC-266 transazioneAperta resta vera in una transazione annidata e dopo la sua chiusura`() {
        val osservati = uow.inTransazione {
            val annidata = uow.inTransazione { Esito.Ok(uow.transazioneAperta) }.atteso()
            Esito.Ok(listOf(annidata, uow.transazioneAperta))
        }.atteso()
        assertEquals(listOf(true, true), osservati)
        assertFalse(uow.transazioneAperta)
    }

    @Test
    fun `AC-266 transazioneAperta torna falsa dopo il rollback su Errore`() {
        uow.inTransazione<Unit> { Esito.Errore(ErroreDiProva.Fallito("no")) }
            .erroreAtteso<ErroreDiProva.Fallito>()
        assertFalse(uow.transazioneAperta)
    }

    @Test
    fun `AC-266 transazioneAperta torna falsa dopo il rollback su eccezione anche annidata`() {
        assertFailsWith<IllegalArgumentException> {
            uow.inTransazione<Unit> { throw IllegalArgumentException("guasto") }
        }
        assertFalse(uow.transazioneAperta)
        assertFailsWith<IllegalArgumentException> {
            uow.inTransazione<Unit> { uow.inTransazione<Unit> { throw IllegalArgumentException("guasto") } }
        }
        assertFalse(uow.transazioneAperta)
    }
}
