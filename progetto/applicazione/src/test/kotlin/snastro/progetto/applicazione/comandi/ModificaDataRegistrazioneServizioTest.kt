package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModificaDataRegistrazioneServizioTest {
    private val progettoId = ProgettoId("progetto-1")
    private val id = RegistrazioneId("id-1")
    private val dataFile = LocalDate.of(2026, 3, 12)
    private val registrazioni = RegistrazioneRepositoryFinta().apply {
        salva(
            Registrazione.aggiungi(
                id = id,
                progettoId = progettoId,
                titolo = "Seduta del 12 marzo",
                riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
                durataMs = 3_600_000,
                dataRegistrazione = dataFile,
                aggiuntaAlle = Instant.parse("2026-03-12T10:00:00Z"),
            ).aggregato,
        )
    }
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni))
    private val servizio = ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)

    @Test
    fun `AC-62 sostituisce la data e pubblica DataRegistrazioneModificata con precedente e nuova`() {
        val nuovaData = LocalDate.of(2026, 3, 10)

        servizio.esegui(ModificaDataRegistrazione(id, nuovaData)).atteso()

        assertEquals(nuovaData, assertNotNull(registrazioni.trova(id)).dataRegistrazione)
        assertEquals(
            listOf(DataRegistrazioneModificata(id, precedente = dataFile, nuova = nuovaData)),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-63 su una Registrazione inesistente restituisce RegistrazioneNonTrovata`() {
        val sconosciuta = RegistrazioneId("id-sconosciuto")

        val errore = servizio.esegui(ModificaDataRegistrazione(sconosciuta, LocalDate.of(2026, 3, 10)))
            .erroreAtteso<ErroreProgetto.RegistrazioneNonTrovata>()

        assertEquals(sconosciuta, errore.id)
        assertEquals(emptyList(), eventi.pubblicati)
    }
}
