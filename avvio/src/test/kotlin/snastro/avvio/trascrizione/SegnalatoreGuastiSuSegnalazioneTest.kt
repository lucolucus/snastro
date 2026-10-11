package snastro.avvio.trascrizione

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.SegnalatoreGuasti
import snastro.trascrizione.applicazione.porte.SegnalatoreGuastiContratto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** ADR 0042 §4: a phase fault reaches the app's log as one line with its cause. */
class SegnalatoreGuastiSuSegnalazioneTest : SegnalatoreGuastiContratto() {
    private val segnalate = mutableListOf<Pair<String, Throwable?>>()

    override fun segnalatore(): SegnalatoreGuasti =
        segnalatoreGuastiSu { messaggio, causa -> segnalate += messaggio to causa }

    @Test
    fun `ADR 0042 un guasto diventa una riga con la Registrazione il motivo e la causa`() {
        val causa = IllegalStateException("diarizzazione nativa")

        segnalatore().guasto(RegistrazioneId("rec-1"), "errore nella separazione delle voci", causa)

        val (messaggio, segnalata) = segnalate.single()
        assertEquals("Elaborazione di rec-1 fallita (errore nella separazione delle voci)", messaggio)
        assertSame(causa, segnalata)
    }
}
