package snastro.avvio.r1

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR 0004: the ML adapters' per-Elaborazione memory is released once, when the Elaborazione terminates. */
class SegnalatoreFaseConRilascioTest {
    private val id = RegistrazioneId("rec-1")

    @Test
    fun `terminata inoltra al delegato e poi rilascia, una fase non rilascia nulla`() {
        val delegato = SegnalatoreFaseFinta()
        val ordine = mutableListOf<String>()
        val segnalatore = SegnalatoreFaseConRilascio(delegato) { ordine += "rilascio dopo ${delegato.terminate}" }

        segnalatore.fase(id, FaseElaborazione.TRASCRIZIONE)
        assertEquals(emptyList(), ordine)

        segnalatore.terminata(id)

        assertEquals(listOf(FaseElaborazione.TRASCRIZIONE), delegato.fasi(id))
        assertEquals(listOf("rilascio dopo [$id]"), ordine)
    }

    @Test
    fun `un rilascio che fallisce non fa mai lanciare terminata`() {
        val delegato = SegnalatoreFaseFinta()
        val segnalatore = SegnalatoreFaseConRilascio(delegato) { error("rilascio nativo fallito") }

        segnalatore.terminata(id)

        assertEquals(listOf(id), delegato.terminate)
    }
}
