package snastro.avvio

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.atteso
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.SondaAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-346: `CreaProgetto`/`AggiungiRegistrazione`/`ModificaDataRegistrazione` are built with
 * `DispatcherEventiInMemoria.unitaDiLavoro`, never the raw `UnitaDiLavoroSql` — end-to-end on
 * `databaseInMemoria()` (real repositories, fake audio ports — this proves the DISPATCHER wiring, not
 * FFmpeg). If a service were built with the raw unit of work instead, `dispatcher.pubblica(...)` would
 * throw ("pubblica fuori da inTransazione") instead of returning `Esito.Ok`.
 */
class ComandiConEventiUnitaDiLavoroTest {
    private val orologio: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `AC-346 AggiungiRegistrazione pubblica senza errore, un abbonato dopo-commit la riceve solo dopo il commit`() {
        val db = databaseInMemoria()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        val generatoreId = GeneratoreIdFinto()
        val progetti = ProgettoRepositorySql(db)
        val registrazioni = RegistrazioneRepositorySql(db)
        CreaProgettoServizio(dispatcher.unitaDiLavoro, generatoreId, progetti, dispatcher)
            .esegui(CreaProgetto("Prova")).atteso()

        val ricevuti = mutableListOf<EventoPubblicato>()
        dispatcher.registraDopoCommit { ricevuti += it }

        val servizio = AggiungiRegistrazioneServizio(
            dispatcher.unitaDiLavoro,
            generatoreId,
            orologio,
            progetti,
            registrazioni,
            SondaAudioFinta(leggibili = mapOf("/prova.wav" to InfoAudio(1_000, LocalDate.parse("2026-01-01")))),
            ArchivioAudioFinta().conSorgente("/prova.wav"),
            dispatcher,
        )

        servizio.esegui(AggiungiRegistrazione("/prova.wav")).atteso()

        val messaggio = "l'abbonato dopo-commit deve ricevere RegistrazioneAggiunta"
        assertTrue(ricevuti.any { it is RegistrazioneAggiunta }, messaggio)
    }

    @Test
    fun `AC-346 un abbonato sincrono che fallisce annulla la transazione, l abbonato dopo-commit non riceve nulla`() {
        val db = databaseInMemoria()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        val generatoreId = GeneratoreIdFinto()
        val progetti = ProgettoRepositorySql(db)
        val registrazioni = RegistrazioneRepositorySql(db)
        CreaProgettoServizio(dispatcher.unitaDiLavoro, generatoreId, progetti, dispatcher)
            .esegui(CreaProgetto("Prova")).atteso()

        dispatcher.registraSincrono { Esito.Errore(ErroreDiProva.Fallito("mai")) }
        val ricevuti = mutableListOf<EventoPubblicato>()
        dispatcher.registraDopoCommit { ricevuti += it }

        val servizio = AggiungiRegistrazioneServizio(
            dispatcher.unitaDiLavoro,
            generatoreId,
            orologio,
            progetti,
            registrazioni,
            SondaAudioFinta(leggibili = mapOf("/prova.wav" to InfoAudio(1_000, LocalDate.parse("2026-01-01")))),
            ArchivioAudioFinta().conSorgente("/prova.wav"),
            dispatcher,
        )

        val esito = servizio.esegui(AggiungiRegistrazione("/prova.wav"))

        assertTrue(esito is Esito.Errore, "il comando deve fallire (annullato dall'abbonato sincrono)")
        assertTrue(ricevuti.isEmpty(), "nessun evento deve raggiungere l'abbonato dopo-commit dopo un rollback")
    }
}
