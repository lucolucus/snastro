package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RinominaRegistrazioneServizioTest {
    private val progettoId = ProgettoId("progetto-1")
    private val id = RegistrazioneId("id-1")
    private val altra = RegistrazioneId("id-2")
    private val registrazioni = RegistrazioneRepositoryFinta().apply {
        salva(unaRegistrazione(id, progettoId, "Seduta del 12 marzo"))
        salva(unaRegistrazione(altra, progettoId, "Intervista Marco"))
        salva(unaRegistrazione(RegistrazioneId("id-3"), ProgettoId("progetto-2"), "Riunione budget"))
    }
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni))
    private val servizio = RinominaRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)

    private fun titoloDi(registrazioneId: RegistrazioneId): String =
        assertNotNull(registrazioni.trova(registrazioneId)).titolo

    @Test
    fun `AC-361 rinomina salva il nuovo titolo e pubblica RegistrazioneRinominata dopo il commit`() {
        servizio.esegui(RinominaRegistrazione(id, "  Consiglio di marzo ")).atteso()

        assertEquals("Consiglio di marzo", titoloDi(id))
        assertEquals(
            listOf(RegistrazioneRinominata(id, precedente = "Seduta del 12 marzo", nuovo = "Consiglio di marzo")),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-361 una Registrazione inesistente restituisce RegistrazioneNonTrovata`() {
        val sconosciuta = RegistrazioneId("id-sconosciuto")

        val errore = servizio.esegui(RinominaRegistrazione(sconosciuta, "Nuovo"))
            .erroreAtteso<ErroreProgetto.RegistrazioneNonTrovata>()

        assertEquals(sconosciuta, errore.id)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-360 un titolo vuoto restituisce TitoloVuoto e nulla cambia`() {
        servizio.esegui(RinominaRegistrazione(id, "   ")).erroreAtteso<ErroreProgetto.TitoloVuoto>()

        assertEquals("Seduta del 12 marzo", titoloDi(id))
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-361 un titolo con la stessa chiave di un altra Registrazione del Progetto restituisce TitoloGiaUsato`() {
        // AC-322's key: pulisci + case-folding — "intervista MARCO. " collides with "Intervista Marco".
        val errore = servizio.esegui(RinominaRegistrazione(id, "intervista MARCO."))
            .erroreAtteso<ErroreProgetto.TitoloGiaUsato>()

        assertEquals("intervista MARCO.", errore.titolo)
        assertEquals("Seduta del 12 marzo", titoloDi(id))
        assertEquals("Intervista Marco", titoloDi(altra))
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-361 il titolo di una Registrazione di un altro Progetto non conta`() {
        servizio.esegui(RinominaRegistrazione(id, "Riunione budget")).atteso()

        assertEquals("Riunione budget", titoloDi(id))
    }

    @Test
    fun `AC-361 la Registrazione stessa e esclusa, cambiare solo maiuscole e minuscole e ammesso`() {
        servizio.esegui(RinominaRegistrazione(id, "SEDUTA DEL 12 MARZO")).atteso()

        assertEquals("SEDUTA DEL 12 MARZO", titoloDi(id))
        assertEquals(1, eventi.pubblicati.size)
    }

    @Test
    fun `AC-360 lo stesso titolo e un no-op, nessun evento pubblicato`() {
        servizio.esegui(RinominaRegistrazione(id, "Seduta del 12 marzo ")).atteso()

        assertEquals("Seduta del 12 marzo", titoloDi(id))
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-361 se la transazione non conferma nulla e salvato e nessun evento arriva dopo il commit`() {
        eventi.registraSincrono { Esito.Errore(ErroreDiProva.Fallito("abbonato sincrono")) }

        servizio.esegui(RinominaRegistrazione(id, "Consiglio di marzo")).erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals("Seduta del 12 marzo", titoloDi(id))
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-362 rinominare non cambia il riferimento audio`() {
        servizio.esegui(RinominaRegistrazione(id, "Consiglio di marzo")).atteso()

        assertEquals(RiferimentoAudio("audio/id-1.m4a"), assertNotNull(registrazioni.trova(id)).riferimentoAudio)
    }

    private fun unaRegistrazione(id: RegistrazioneId, progettoId: ProgettoId, titolo: String): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000,
            dataRegistrazione = LocalDate.of(2026, 3, 12),
            aggiuntaAlle = Instant.parse("2026-03-12T10:00:00Z"),
        ).aggregato
}
