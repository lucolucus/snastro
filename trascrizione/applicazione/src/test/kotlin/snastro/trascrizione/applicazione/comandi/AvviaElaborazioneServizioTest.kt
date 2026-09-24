package snastro.trascrizione.applicazione.comandi

import org.junit.jupiter.api.Test
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.NumeroPersoneFuoriIntervallo
import snastro.trascrizione.dominio.ErroreTrascrizione.RegistrazioneNonTrovata
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvviaElaborazioneServizioTest {

    @Test
    fun `AC-64 AvviaElaborazione su una Registrazione senza Elaborazioni crea un Elaborazione in_attesa`() {
        val elaborazioni = ElaborazioneRepositoryFinta()

        unServizio(elaborazioni = elaborazioni).esegui(AvviaElaborazione(REGISTRAZIONE)).atteso()

        val create = elaborazioni.diRegistrazione(REGISTRAZIONE)
        assertEquals(listOf(ElaborazioneId("id-1")), create.map { it.id })
        assertEquals(listOf(StatoElaborazione.IN_ATTESA), create.map { it.stato })
    }

    @Test
    fun `INV-4 con un Elaborazione in_attesa gia presente rifiuta con ElaborazioneGiaAperta e non salva nulla`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        elaborazioni.salva(unaElaborazione(StatoElaborazione.IN_ATTESA, registrazioneId = REGISTRAZIONE)).atteso()

        val errore = unServizio(elaborazioni = elaborazioni)
            .esegui(AvviaElaborazione(REGISTRAZIONE))
            .erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(ElaborazioneGiaAperta(REGISTRAZIONE), errore)
        assertEquals(1, elaborazioni.diRegistrazione(REGISTRAZIONE).size)
    }

    @Test
    fun `INV-4 con un Elaborazione in_corso gia presente rifiuta con ElaborazioneGiaAperta`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        elaborazioni.salva(unaElaborazione(StatoElaborazione.IN_CORSO, registrazioneId = REGISTRAZIONE)).atteso()

        val errore = unServizio(elaborazioni = elaborazioni)
            .esegui(AvviaElaborazione(REGISTRAZIONE))
            .erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(ElaborazioneGiaAperta(REGISTRAZIONE), errore)
        assertEquals(1, elaborazioni.diRegistrazione(REGISTRAZIONE).size)
    }

    @Test
    fun `AC-65 dopo una fallita crea una nuova Elaborazione in_attesa e la fallita resta nello storico`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val vecchia = ElaborazioneId("elaborazione-vecchia")
        elaborazioni.salva(
            unaElaborazione(StatoElaborazione.FALLITA, id = vecchia, registrazioneId = REGISTRAZIONE),
        ).atteso()

        unServizio(elaborazioni = elaborazioni).esegui(AvviaElaborazione(REGISTRAZIONE)).atteso()

        val tutte = elaborazioni.diRegistrazione(REGISTRAZIONE)
        assertEquals(2, tutte.size)
        assertEquals(
            mapOf(vecchia to StatoElaborazione.FALLITA, ElaborazioneId("id-1") to StatoElaborazione.IN_ATTESA),
            tutte.associate { it.id to it.stato },
        )
    }

    @Test
    fun `AC-66 se il repository segnala la violazione dell indice il servizio restituisce lo stesso ErroreDominio`() {
        val errore = unServizio(elaborazioni = ElaborazioneRepositoryCheSegnalaLaGara())
            .esegui(AvviaElaborazione(REGISTRAZIONE))
            .erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(ElaborazioneGiaAperta(REGISTRAZIONE), errore)
    }

    @Test
    fun `AC-67 registrazione inesistente restituisce RegistrazioneNonTrovata e non crea nulla`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val servizio = unServizio(registrazioni = LettoreRegistrazioneFinta(), elaborazioni = elaborazioni)

        val errore = servizio.esegui(AvviaElaborazione(REGISTRAZIONE)).erroreAtteso<RegistrazioneNonTrovata>()

        assertEquals(RegistrazioneNonTrovata(REGISTRAZIONE), errore)
        assertEquals(emptyList(), elaborazioni.diRegistrazione(REGISTRAZIONE))
    }

    @Test
    fun `AC-369 senza numeroPersone l Elaborazione in_attesa non ha numero`() {
        val elaborazioni = ElaborazioneRepositoryFinta()

        unServizio(elaborazioni = elaborazioni).esegui(AvviaElaborazione(REGISTRAZIONE)).atteso()

        val creata = elaborazioni.diRegistrazione(REGISTRAZIONE).single()
        assertEquals(StatoElaborazione.IN_ATTESA, creata.stato)
        assertNull(creata.numeroPersone)
    }

    @Test
    fun `AC-369 con numeroPersone 4 l Elaborazione in_attesa ha numeroPersone 4`() {
        val elaborazioni = ElaborazioneRepositoryFinta()

        unServizio(elaborazioni = elaborazioni).esegui(AvviaElaborazione(REGISTRAZIONE, numeroPersone = 4)).atteso()

        val creata = elaborazioni.diRegistrazione(REGISTRAZIONE).single()
        assertEquals(StatoElaborazione.IN_ATTESA, creata.stato)
        assertEquals(4, creata.numeroPersone?.valore)
    }

    @Test
    fun `AC-369 con numeroPersone 0 o 11 restituisce NumeroPersoneFuoriIntervallo e non crea righe`() {
        listOf(0, 11).forEach { n ->
            val elaborazioni = ElaborazioneRepositoryFinta()

            val errore = unServizio(elaborazioni = elaborazioni)
                .esegui(AvviaElaborazione(REGISTRAZIONE, numeroPersone = n))
                .erroreAtteso<NumeroPersoneFuoriIntervallo>()

            assertEquals(NumeroPersoneFuoriIntervallo(n), errore)
            assertEquals(emptyList(), elaborazioni.diRegistrazione(REGISTRAZIONE), "n = $n")
        }
    }

    @Test
    fun `AC-369 la riprova dopo una fallita salva il valore inviato e la fallita resta invariata`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val vecchia = ElaborazioneId("elaborazione-vecchia")
        val tre = NumeroPersone.di(3).atteso()
        elaborazioni.salva(
            unaElaborazione(StatoElaborazione.FALLITA, vecchia, REGISTRAZIONE, numeroPersone = tre),
        ).atteso()

        unServizio(elaborazioni = elaborazioni).esegui(AvviaElaborazione(REGISTRAZIONE, numeroPersone = 5)).atteso()

        val tutte = elaborazioni.diRegistrazione(REGISTRAZIONE).associateBy { it.id }
        assertEquals(StatoElaborazione.FALLITA, tutte.getValue(vecchia).stato)
        assertEquals(tre, tutte.getValue(vecchia).numeroPersone)
        assertEquals(StatoElaborazione.IN_ATTESA, tutte.getValue(ElaborazioneId("id-1")).stato)
        assertEquals(5, tutte.getValue(ElaborazioneId("id-1")).numeroPersone?.valore)
    }

    /** A double that lets the pre-check pass (empty) but refuses `salva` like the ADR 0007 index would (AC-66). */
    private class ElaborazioneRepositoryCheSegnalaLaGara : ElaborazioneRepository {
        override fun diRegistrazione(id: RegistrazioneId): List<Elaborazione> = emptyList()

        override fun inAttesa(): List<Elaborazione> = emptyList()

        override fun inCorso(): List<Elaborazione> = emptyList()

        override fun trova(id: ElaborazioneId): Elaborazione? = null

        override fun salva(e: Elaborazione): Esito<Unit> = Esito.Errore(ElaborazioneGiaAperta(e.registrazioneId))

        override fun rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> = error("non usato da AvviaElaborazione")
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)

        fun unaVista(id: RegistrazioneId = REGISTRAZIONE): RegistrazioneVista = RegistrazioneVista(
            registrazioneId = id,
            progettoId = ProgettoId("progetto-1"),
            titolo = "Riunione di lunedi",
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 9, 21),
            durataMs = 3_600_000L,
        )

        fun unServizio(
            uow: UnitaDiLavoro = UnitaDiLavoroFinta(),
            generatoreId: GeneratoreId = GeneratoreIdFinto(),
            registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to unaVista())),
            elaborazioni: ElaborazioneRepository = ElaborazioneRepositoryFinta(),
        ): AvviaElaborazioneServizio =
            AvviaElaborazioneServizio(uow, generatoreId, OROLOGIO, registrazioni, elaborazioni)
    }
}
