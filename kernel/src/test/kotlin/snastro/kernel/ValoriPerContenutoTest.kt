package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ValoriPerContenutoTest {
    private val registrazione = RegistrazioneId("id-1")

    @Test
    fun `AC-6 CampioniAudio con lo stesso contenuto sono uguali con lo stesso hashCode`() {
        val a = CampioniAudio(floatArrayOf(0.1f, -0.5f, 1f))
        val b = CampioniAudio(floatArrayOf(0.1f, -0.5f, 1f))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, CampioniAudio(floatArrayOf(0.1f, -0.5f)))
    }

    @Test
    fun `AC-6 EstrattoRef con lo stesso contenuto sono uguali con lo stesso hashCode`() {
        val a = EstrattoRef(registrazione, listOf(IntervalloMs(0, 1000), IntervalloMs(5000, 6000)))
        val b = EstrattoRef(RegistrazioneId("id-1"), listOf(IntervalloMs(0, 1000), IntervalloMs(5000, 6000)))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, EstrattoRef(registrazione, listOf(IntervalloMs(0, 1000))))
    }

    @Test
    fun `EstrattoRef rifiuta una lista vuota di intervalli`() {
        assertFailsWith<IllegalArgumentException> { EstrattoRef(registrazione, emptyList()) }
    }

    @Test
    fun `EstrattoRef rifiuta intervalli non ordinati per inizio`() {
        assertFailsWith<IllegalArgumentException> {
            EstrattoRef(registrazione, listOf(IntervalloMs(5000, 6000), IntervalloMs(0, 1000)))
        }
    }

    @Test
    fun `EstrattoRef accetta al massimo 10 secondi in totale`() {
        EstrattoRef(registrazione, listOf(IntervalloMs(0, 4000), IntervalloMs(20_000, 26_000)))
        assertFailsWith<IllegalArgumentException> {
            EstrattoRef(registrazione, listOf(IntervalloMs(0, 4000), IntervalloMs(20_000, 26_001)))
        }
    }

    @Test
    fun `id e riferimenti sono uguali per valore`() {
        assertEquals(VoceRef(registrazione, VoceId(2)), VoceRef(RegistrazioneId("id-1"), VoceId(2)))
        assertEquals(SegmentoId(3), SegmentoId(3))
        assertEquals(RiferimentoAudio("audio/id-1.mp3"), RiferimentoAudio("audio/id-1.mp3"))
    }
}
