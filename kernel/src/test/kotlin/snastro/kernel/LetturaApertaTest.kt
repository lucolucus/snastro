package snastro.kernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * AC-C27: ONE [UnitaDiLavoroFinta] implements both ports;
 * [UnitaDiLavoroFinta.letturaAperta] tracks the outermost read.
 */
class LetturaApertaTest {
    private val uow = UnitaDiLavoroFinta()

    @Test
    fun `AC-C27 la stessa Finta e UnitaDiLavoro e LetturaCoerente`() {
        val scrittura: UnitaDiLavoro = uow
        val lettura: LetturaCoerente = uow
        assertSame<Any>(scrittura, lettura)
    }

    @Test
    fun `AC-C27 letturaAperta e vera solo dentro inLettura esterna e annidata in essa`() {
        assertFalse(uow.letturaAperta)
        val osservati = uow.inLettura {
            val annidata = uow.inLettura { uow.letturaAperta to uow.transazioneAperta }
            listOf(uow.letturaAperta to uow.transazioneAperta, annidata)
        }
        assertEquals(listOf(true to false, true to false), osservati)
        assertFalse(uow.letturaAperta)
    }

    @Test
    fun `AC-C27 una lettura annidata in una transazione non apre una lettura e la transazione resta aperta`() {
        val osservati = uow.inTransazione {
            Esito.Ok(uow.inLettura { uow.letturaAperta to uow.transazioneAperta })
        }.atteso()
        assertEquals(false to true, osservati)
        assertFalse(uow.transazioneAperta)
    }

    @Test
    fun `AC-C27 letturaAperta torna falsa dopo un eccezione e dopo un inTransazione rifiutato`() {
        assertFailsWith<IllegalArgumentException> { uow.inLettura { throw IllegalArgumentException("guasto") } }
        assertFalse(uow.letturaAperta)
        assertFailsWith<IllegalStateException> { uow.inLettura { uow.inTransazione { Esito.Ok(Unit) } } }
        assertFalse(uow.letturaAperta)
        assertFalse(uow.transazioneAperta)
        assertEquals(true, uow.inTransazione { Esito.Ok(uow.transazioneAperta) }.atteso())
    }
}
