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
    fun `AC-442 TrascrittoSostituito ha solo registrazioneId`() {
        val evento: EventoPubblicato = TrascrittoSostituito(registrazioneId = registrazione)
        assertEquals(TrascrittoSostituito(RegistrazioneId("id-1")), evento)
        assertEquals(listOf("registrazioneId: RegistrazioneId"), FormaEventi.di("TrascrittoSostituito"))
    }

    @Test
    fun `AC-470 ElaborazioneAnnullata ha solo registrazioneId`() {
        val evento: EventoPubblicato = ElaborazioneAnnullata(registrazioneId = registrazione)
        assertEquals(ElaborazioneAnnullata(RegistrazioneId("id-1")), evento)
        assertEquals(listOf("registrazioneId: RegistrazioneId"), FormaEventi.di("ElaborazioneAnnullata"))
    }

    @Test
    fun `AC-442 TrascrittoSostituito arriva al sincrono dentro la transazione e al dopo-commit solo dopo il COMMIT`() {
        val consegna = Consegna()

        consegna.inTransazione(TrascrittoSostituito(registrazione), conferma = false)
        assertEquals(listOf<EventoPubblicato>(TrascrittoSostituito(registrazione)), consegna.sincroni)
        assertEquals(emptyList(), consegna.dopoCommit, "mai dopo un rollback")

        consegna.inTransazione(TrascrittoSostituito(registrazione), conferma = true)
        assertEquals(listOf(true, true), consegna.sincroniDentroLaTransazione)
        assertEquals(listOf<EventoPubblicato>(TrascrittoSostituito(registrazione)), consegna.dopoCommit)
    }

    @Test
    fun `AC-470 ElaborazioneAnnullata arriva al dopo-commit solo dopo il COMMIT e mai dopo un rollback`() {
        val consegna = Consegna()

        consegna.inTransazione(ElaborazioneAnnullata(registrazione), conferma = false)
        assertEquals(emptyList(), consegna.dopoCommit)

        consegna.inTransazione(ElaborazioneAnnullata(registrazione), conferma = true)
        assertEquals(listOf<EventoPubblicato>(ElaborazioneAnnullata(registrazione)), consegna.dopoCommit)
    }

    @Test
    fun `AC-15 gli eventi di Elaborazione sono data class di soli val che implementano EventoPubblicato`() {
        listOf(
            "ElaborazioneAvviata",
            "ElaborazioneCompletata",
            "ElaborazioneFallita",
            "TrascrittoSostituito",
            "ElaborazioneAnnullata",
        ).forEach(FormaEventi::verificaEventoPubblicato)
    }
}
