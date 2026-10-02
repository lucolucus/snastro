package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundary `eventi-elaborazione` (building-blocks.yaml): AC-14 shape of each event, AC-15 CR-5. */
class EventiElaborazioneTest {
    private val registrazione = RegistrazioneId("id-1")
    private val incontro = IncontroId("incontro-1")
    private val sostituito = TrascrittoSostituito(registrazione, incontro, setOf(VoceId(2)))

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
    fun `AC-I23 ElaborazioneCompletata ha registrazioneId e incontroId`() {
        val evento: EventoPubblicato = ElaborazioneCompletata(registrazioneId = registrazione, incontroId = incontro)
        assertEquals(ElaborazioneCompletata(RegistrazioneId("id-1"), IncontroId("incontro-1")), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "incontroId: IncontroId"),
            FormaEventi.di("ElaborazioneCompletata"),
        )
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
    fun `AC-I23 TrascrittoSostituito ha registrazioneId, incontroId e vociRimosse`() {
        val evento: EventoPubblicato =
            TrascrittoSostituito(registrazioneId = registrazione, incontroId = incontro, vociRimosse = setOf(VoceId(2)))
        assertEquals(TrascrittoSostituito(RegistrazioneId("id-1"), IncontroId("incontro-1"), setOf(VoceId(2))), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "incontroId: IncontroId", "vociRimosse: Set<VoceId>"),
            FormaEventi.di("TrascrittoSostituito"),
        )
    }

    @Test
    fun `AC-I23 TrascrittoEliminato ha registrazioneId, incontroId e vociRimosse`() {
        val evento: EventoPubblicato =
            TrascrittoEliminato(registrazioneId = registrazione, incontroId = incontro, vociRimosse = setOf(VoceId(4)))
        assertEquals(TrascrittoEliminato(RegistrazioneId("id-1"), IncontroId("incontro-1"), setOf(VoceId(4))), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "incontroId: IncontroId", "vociRimosse: Set<VoceId>"),
            FormaEventi.di("TrascrittoEliminato"),
        )
    }

    @Test
    fun `AC-I23 TrascrittoEliminato arriva al sincrono dentro la transazione e al dopo-commit solo dopo il COMMIT`() {
        val consegna = Consegna()
        val eliminato = TrascrittoEliminato(registrazione, incontro, setOf(VoceId(4)))

        consegna.inTransazione(eliminato, conferma = false)
        assertEquals(listOf<EventoPubblicato>(eliminato), consegna.sincroni)
        assertEquals(emptyList(), consegna.dopoCommit, "mai dopo un rollback")

        consegna.inTransazione(eliminato, conferma = true)
        assertEquals(listOf(true, true), consegna.sincroniDentroLaTransazione)
        assertEquals(listOf<EventoPubblicato>(eliminato), consegna.dopoCommit)
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

        consegna.inTransazione(sostituito, conferma = false)
        assertEquals(listOf<EventoPubblicato>(sostituito), consegna.sincroni)
        assertEquals(emptyList(), consegna.dopoCommit, "mai dopo un rollback")

        consegna.inTransazione(sostituito, conferma = true)
        assertEquals(listOf(true, true), consegna.sincroniDentroLaTransazione)
        assertEquals(listOf<EventoPubblicato>(sostituito), consegna.dopoCommit)
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
            "TrascrittoEliminato",
            "ElaborazioneAnnullata",
        ).forEach(FormaEventi::verificaEventoPubblicato)
    }
}
