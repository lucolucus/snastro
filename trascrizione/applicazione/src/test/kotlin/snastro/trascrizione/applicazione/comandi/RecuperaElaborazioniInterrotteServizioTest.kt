package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecuperaElaborazioniInterrotteServizioTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni))
    private val servizio = RecuperaElaborazioniInterrotteServizio(eventi.unitaDiLavoro, elaborazioni, eventi)

    @Test
    fun `AC-74 un in_corso senza esecuzione viva diventa fallita interrotta ed e riprovabile`() {
        val id = RegistrazioneId("registrazione-1")
        elaborazioni.salva(
            unaElaborazione(stato = StatoElaborazione.IN_CORSO, id = ElaborazioneId("elab-1"), registrazioneId = id),
        ).atteso()

        servizio.esegui(RecuperaElaborazioniInterrotte).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita, "l'Elaborazione interrotta deve risultare fallita")
        assertEquals("interrotta", salvata.motivoFallimento)
        assertTrue(
            salvata.terminale,
            "fallita e' uno stato terminale: una nuova Elaborazione puo' essere accodata (INV-4)",
        )
        assertEquals(listOf(ElaborazioneFallita(id, "interrotta")), eventi.pubblicati)
    }

    @Test
    fun `AC-75 in_attesa completata e fallita restano intoccate, solo in_corso diventa fallita`() {
        val inAttesa = RegistrazioneId("registrazione-attesa")
        val inCorso = RegistrazioneId("registrazione-corso")
        val completata = RegistrazioneId("registrazione-completata")
        val giaFallita = RegistrazioneId("registrazione-fallita")
        elaborazioni.salva(
            unaElaborazione(stato = StatoElaborazione.IN_ATTESA, id = ElaborazioneId("e1"), registrazioneId = inAttesa),
        ).atteso()
        elaborazioni.salva(
            unaElaborazione(stato = StatoElaborazione.IN_CORSO, id = ElaborazioneId("e2"), registrazioneId = inCorso),
        ).atteso()
        elaborazioni.salva(
            unaElaborazione(
                stato = StatoElaborazione.COMPLETATA,
                id = ElaborazioneId("e3"),
                registrazioneId = completata,
            ),
        ).atteso()
        elaborazioni.salva(
            unaElaborazione(
                stato = StatoElaborazione.FALLITA,
                id = ElaborazioneId("e4"),
                registrazioneId = giaFallita,
                motivo = "guasto originale",
            ),
        ).atteso()

        servizio.esegui(RecuperaElaborazioniInterrotte).atteso()

        assertEquals(listOf(inAttesa), elaborazioni.inAttesa().map { it.registrazioneId })
        val completataSalvata = elaborazioni.diRegistrazione(completata).single()
        assertTrue(completataSalvata.completata)
        val fallitaOriginaria = elaborazioni.diRegistrazione(giaFallita).single()
        assertEquals("guasto originale", fallitaOriginaria.motivoFallimento)
        val eraInCorso = elaborazioni.diRegistrazione(inCorso).single()
        assertTrue(eraInCorso.fallita)
        assertEquals("interrotta", eraInCorso.motivoFallimento)
        assertEquals(listOf(ElaborazioneFallita(inCorso, "interrotta")), eventi.pubblicati)
    }

    @Test
    fun `senza in_corso il comando non ha effetti`() {
        val esito = servizio.esegui(RecuperaElaborazioniInterrotte)

        esito.atteso()
        assertEquals(emptyList(), eventi.pubblicati)
    }
}
