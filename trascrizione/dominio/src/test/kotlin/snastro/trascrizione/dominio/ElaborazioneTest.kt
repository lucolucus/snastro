package snastro.trascrizione.dominio

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElaborazioneTest {
    private val id = ElaborazioneId("id-1")
    private val registrazioneId = RegistrazioneId("id-2")

    // --- allowed transitions: each returns its event ---------------------------------------------

    @Test
    fun `INV-3 accoda crea un Elaborazione in_attesa aperta e restituisce ElaborazioneAccodata`() {
        val creato = Elaborazione.accoda(id, registrazioneId, CREATA_ALLE, numeroPersone = null)

        val e = creato.aggregato
        assertEquals(ElaborazioneAccodata(id, registrazioneId, CREATA_ALLE), creato.evento)
        assertEquals(StatoElaborazione.IN_ATTESA, e.stato)
        assertEquals(id, e.id)
        assertEquals(registrazioneId, e.registrazioneId)
        assertEquals(CREATA_ALLE, e.creataAlle)
        assertNull(e.avviataAlle)
        assertNull(e.motivoFallimento)
        assertPredicati(e, aperta = true, completata = false, fallita = false)
    }

    @Test
    fun `INV-3 da in_attesa avvia porta in_corso e restituisce ElaborazioneAvviata`() {
        val e = unaElaborazione(StatoElaborazione.IN_ATTESA)

        val evento = e.avvia(AVVIATA_ALLE).atteso()

        assertEquals(ElaborazioneAvviata(id, registrazioneId, AVVIATA_ALLE), evento)
        assertEquals(StatoElaborazione.IN_CORSO, e.stato)
        assertPredicati(e, aperta = true, completata = false, fallita = false)
    }

    @Test
    fun `INV-3 da in_corso completa porta a completata terminale e restituisce ElaborazioneCompletata`() {
        val e = unaElaborazione(StatoElaborazione.IN_CORSO)

        val evento = e.completa().atteso()

        assertEquals(ElaborazioneCompletata(id, registrazioneId), evento)
        assertEquals(StatoElaborazione.COMPLETATA, e.stato)
        assertPredicati(e, aperta = false, completata = true, fallita = false)
    }

    @Test
    fun `INV-3 da in_corso fallisci porta a fallita terminale e restituisce ElaborazioneFallita`() {
        val e = unaElaborazione(StatoElaborazione.IN_CORSO)

        val evento = e.fallisci("Il modello non risponde").atteso()

        assertEquals(ElaborazioneFallita(id, registrazioneId, "Il modello non risponde"), evento)
        assertEquals(StatoElaborazione.FALLITA, e.stato)
        assertPredicati(e, aperta = false, completata = false, fallita = true)
    }

    // --- forbidden transitions: Errore(TransizioneNonAmmessa), state unchanged -------------------

    @Test
    fun `INV-3 da in_attesa completa salta in_corso e non e ammessa`() =
        nonAmmessa(StatoElaborazione.IN_ATTESA, StatoElaborazione.COMPLETATA) { completa() }

    @Test
    fun `INV-3 da in_attesa fallisci salta in_corso e non e ammessa`() =
        nonAmmessa(StatoElaborazione.IN_ATTESA, StatoElaborazione.FALLITA) { fallisci("motivo") }

    @Test
    fun `INV-3 da in_corso avvia di nuovo non e ammessa`() =
        nonAmmessa(StatoElaborazione.IN_CORSO, StatoElaborazione.IN_CORSO) { avvia(AVVIATA_ALLE.plusSeconds(60)) }

    @Test
    fun `INV-3 da completata avvia non e ammessa`() =
        nonAmmessa(StatoElaborazione.COMPLETATA, StatoElaborazione.IN_CORSO) { avvia(AVVIATA_ALLE.plusSeconds(60)) }

    @Test
    fun `INV-3 da completata completa di nuovo non e ammessa`() =
        nonAmmessa(StatoElaborazione.COMPLETATA, StatoElaborazione.COMPLETATA) { completa() }

    @Test
    fun `INV-3 da completata fallisci non e ammessa`() =
        nonAmmessa(StatoElaborazione.COMPLETATA, StatoElaborazione.FALLITA) { fallisci("motivo") }

    @Test
    fun `INV-3 da fallita avvia non e ammessa`() =
        nonAmmessa(StatoElaborazione.FALLITA, StatoElaborazione.IN_CORSO) { avvia(AVVIATA_ALLE.plusSeconds(60)) }

    @Test
    fun `INV-3 da fallita completa non e ammessa`() =
        nonAmmessa(StatoElaborazione.FALLITA, StatoElaborazione.COMPLETATA) { completa() }

    @Test
    fun `INV-3 da fallita fallisci di nuovo non e ammessa`() =
        nonAmmessa(StatoElaborazione.FALLITA, StatoElaborazione.FALLITA) { fallisci("un altro motivo") }

    // --- AC-19 -------------------------------------------------------------------------------------

    @Test
    fun `AC-19 avvia registra avviataAlle`() {
        val e = unaElaborazione(StatoElaborazione.IN_ATTESA)

        e.avvia(AVVIATA_ALLE).atteso()

        assertEquals(AVVIATA_ALLE, e.avviataAlle)
    }

    @Test
    fun `AC-19 fallisci conserva il motivo`() {
        val e = unaElaborazione(StatoElaborazione.IN_CORSO)

        e.fallisci("Il file audio non si puo leggere").atteso()

        assertEquals("Il file audio non si puo leggere", e.motivoFallimento)
        assertEquals(AVVIATA_ALLE, e.avviataAlle)
    }

    // --- AC-367 ------------------------------------------------------------------------------------

    @Test
    fun `AC-367 accoda fissa numeroPersone alla creazione`() {
        val quattro = NumeroPersone.di(4).atteso()

        assertEquals(quattro, Elaborazione.accoda(id, registrazioneId, CREATA_ALLE, quattro).aggregato.numeroPersone)
        assertNull(Elaborazione.accoda(id, registrazioneId, CREATA_ALLE, numeroPersone = null).aggregato.numeroPersone)
    }

    @Test
    fun `AC-367 nessuna transizione cambia numeroPersone`() {
        listOf(NumeroPersone.di(4).atteso(), null).forEach { numero ->
            val completata = Elaborazione.accoda(id, registrazioneId, CREATA_ALLE, numero).aggregato
            completata.avvia(AVVIATA_ALLE).atteso()
            assertEquals(numero, completata.numeroPersone, "dopo avvia")
            completata.completa().atteso()
            assertEquals(numero, completata.numeroPersone, "dopo completa")

            val fallita = unaElaborazione(StatoElaborazione.IN_CORSO, numeroPersone = numero)
            fallita.fallisci("motivo").atteso()
            assertEquals(numero, fallita.numeroPersone, "dopo fallisci")

            val rifiutata = unaElaborazione(StatoElaborazione.FALLITA, numeroPersone = numero)
            rifiutata.avvia(AVVIATA_ALLE).erroreAtteso<ErroreTrascrizione.TransizioneNonAmmessa>()
            assertEquals(numero, rifiutata.numeroPersone, "dopo una transizione rifiutata")
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    private fun nonAmmessa(
        da: StatoElaborazione,
        verso: StatoElaborazione,
        transizione: Elaborazione.() -> Esito<*>,
    ) {
        val e = unaElaborazione(da)
        val prima = Istantanea(e)

        val errore = e.transizione().erroreAtteso<ErroreTrascrizione.TransizioneNonAmmessa>()

        assertEquals(ErroreTrascrizione.TransizioneNonAmmessa(id, da, verso), errore)
        assertEquals(prima, Istantanea(e), "lo stato non deve cambiare")
    }

    private data class Istantanea(
        val stato: StatoElaborazione,
        val avviataAlle: java.time.Instant?,
        val motivoFallimento: String?,
    ) {
        constructor(e: Elaborazione) : this(e.stato, e.avviataAlle, e.motivoFallimento)
    }

    private fun assertPredicati(e: Elaborazione, aperta: Boolean, completata: Boolean, fallita: Boolean) {
        assertEquals(aperta, e.aperta, "aperta")
        assertEquals(completata, e.completata, "completata")
        assertEquals(fallita, e.fallita, "fallita")
        assertEquals(completata || fallita, e.terminale, "terminale")
        if (e.terminale) assertFalse(e.aperta) else assertTrue(e.aperta)
    }
}
