package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundary `eventi-elaborazione` (building-blocks.yaml): AC-14 shape of each event, AC-15 CR-5. */
class EventiElaborazioneTest {
    private val registrazione = RegistrazioneId("id-1")

    @Test
    fun `AC-14 ElaborazioneAvviata ha registrazioneId e avviataAlle`() {
        val alle = Instant.parse("2026-09-23T10:15:30Z")
        val evento: EventoPubblicato = ElaborazioneAvviata(registrazioneId = registrazione, avviataAlle = alle)
        assertEquals(ElaborazioneAvviata(RegistrazioneId("id-1"), alle), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "avviataAlle: Instant"),
            FormaEventi.di("ElaborazioneAvviata"),
        )
    }

    @Test
    fun `AC-14 ElaborazioneCompletata ha solo registrazioneId`() {
        val evento: EventoPubblicato = ElaborazioneCompletata(registrazioneId = registrazione)
        assertEquals(ElaborazioneCompletata(RegistrazioneId("id-1")), evento)
        assertEquals(listOf("registrazioneId: RegistrazioneId"), FormaEventi.di("ElaborazioneCompletata"))
    }

    @Test
    fun `AC-14 ElaborazioneFallita ha registrazioneId e motivo in italiano`() {
        val evento: EventoPubblicato =
            ElaborazioneFallita(registrazioneId = registrazione, motivo = "nessun parlato rilevato")
        assertEquals(ElaborazioneFallita(RegistrazioneId("id-1"), "nessun parlato rilevato"), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "motivo: String"),
            FormaEventi.di("ElaborazioneFallita"),
        )
    }

    @Test
    fun `AC-15 gli eventi di Elaborazione sono data class di soli val che implementano EventoPubblicato`() {
        listOf("ElaborazioneAvviata", "ElaborazioneCompletata", "ElaborazioneFallita")
            .forEach(FormaEventi::verificaEventoPubblicato)
    }
}
