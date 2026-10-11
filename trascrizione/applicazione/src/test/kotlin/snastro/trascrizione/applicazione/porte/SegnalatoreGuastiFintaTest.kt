package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SegnalatoreGuastiFintaTest : SegnalatoreGuastiContratto() {
    override fun segnalatore(): SegnalatoreGuasti = SegnalatoreGuastiFinta()

    @Test
    fun `ADR 0042 la Finta registra ogni guasto in ordine con la stessa causa`() {
        val s = SegnalatoreGuastiFinta()
        val id = RegistrazioneId("registrazione-1")
        val causa = IllegalStateException("guasto")
        assertEquals(emptyList(), s.guasti)

        s.guasto(id, "errore nella trascrizione", causa)

        val ricevuto = s.guasti.single()
        assertEquals(GuastoSegnalato(id, "errore nella trascrizione", causa), ricevuto)
        assertSame(causa, ricevuto.causa)
    }
}
