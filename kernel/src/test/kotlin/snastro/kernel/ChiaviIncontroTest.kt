package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ChiaviIncontroTest {
    @Test
    fun `AC-I10 VoceRef e uguale per stesso Incontro e stessa Voce, diversa per un altro Incontro`() {
        val a = VoceRef(IncontroId("i1"), VoceId(2))
        assertEquals(a, VoceRef(IncontroId("i1"), VoceId(2)))
        assertEquals(a.hashCode(), VoceRef(IncontroId("i1"), VoceId(2)).hashCode())
        assertNotEquals(a, VoceRef(IncontroId("i2"), VoceId(2)))
        assertNotEquals(a, VoceRef(IncontroId("i1"), VoceId(3)))
    }

    @Test
    fun `AC-I10 SegmentoRef e uguale per la coppia registrazioneId e segmentoId`() {
        val a = SegmentoRef(RegistrazioneId("r1"), SegmentoId(5))
        assertEquals(a, SegmentoRef(RegistrazioneId("r1"), SegmentoId(5)))
        assertNotEquals(a, SegmentoRef(RegistrazioneId("r2"), SegmentoId(5)))
        assertNotEquals(a, SegmentoRef(RegistrazioneId("r1"), SegmentoId(6)))
    }

    @Test
    fun `AC-I10 IncontroId rifiuta un valore vuoto o di soli spazi`() {
        assertFailsWith<IllegalArgumentException> { IncontroId("") }
        assertFailsWith<IllegalArgumentException> { IncontroId("  ") }
        assertEquals("i1", IncontroId("i1").valore)
    }
}
