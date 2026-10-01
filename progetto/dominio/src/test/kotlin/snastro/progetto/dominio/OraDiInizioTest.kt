package snastro.progetto.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OraDiInizioTest {
    @Test
    fun `INV-I14 gli estremi validi 00 00 00 e 23 59 59 sono accettati`() {
        assertEquals(LocalTime.MIDNIGHT, OraDiInizio.di("00:00:00").atteso()?.valore)
        assertEquals(LocalTime.of(23, 59, 59), OraDiInizio.di("23:59:59").atteso()?.valore)
    }

    @Test
    fun `INV-I14 un ora fuori da 00 00 00 - 24 00 00 o non un ora e rifiutata`() {
        listOf("24:00:00", "12:60:00", "-1", "").forEach { testo ->
            OraDiInizio.di(testo).erroreAtteso<ErroreProgetto.OraDiInizioNonValida>()
        }
    }

    @Test
    fun `INV-I14 null e l ora vuota, legale`() {
        assertNull(OraDiInizio.di(null as String?).atteso())
    }

    @Test
    fun `INV-I14 da un LocalTime accetta il secondo e rifiuta le frazioni di secondo`() {
        assertEquals(LocalTime.of(10, 25), OraDiInizio.di(LocalTime.of(10, 25)).atteso().valore)
        OraDiInizio.di(LocalTime.of(10, 25, 0, 1)).erroreAtteso<ErroreProgetto.OraDiInizioNonValida>()
    }
}
