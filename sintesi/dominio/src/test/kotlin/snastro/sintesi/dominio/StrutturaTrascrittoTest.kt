package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrutturaTrascrittoTest {
    @Test
    fun `INV-S7 la chiave ordina le coppie per segmentoId qualunque sia l ordine di ingresso`() {
        val s = unaStruttura(3 to 1, 1 to 1, 2 to 2)

        assertEquals("1:1,2:2,3:1", s.chiave)
        assertEquals(unaStruttura(1 to 1, 2 to 2, 3 to 1), s)
        assertEquals("", unaStruttura().let { StrutturaTrascritto.di(emptyList()) }.chiave)
    }

    @Test
    fun `INV-S7 contiene voceDi e voci leggono l assegnazione`() {
        val s = unaStruttura(1 to 1, 2 to 2, 3 to 1)

        assertTrue(s.contiene(SegmentoId(2)))
        assertFalse(s.contiene(SegmentoId(9)))
        assertEquals(VoceId(2), s.voceDi(SegmentoId(2)))
        assertNull(s.voceDi(SegmentoId(9)))
        assertEquals(setOf(VoceId(1), VoceId(2)), s.voci)
    }

    @Test
    fun `INV-S7 un segmentoId ripetuto e un errore del programmatore`() {
        assertFailsWith<IllegalArgumentException> { unaStruttura(1 to 1, 1 to 2) }
    }
}
