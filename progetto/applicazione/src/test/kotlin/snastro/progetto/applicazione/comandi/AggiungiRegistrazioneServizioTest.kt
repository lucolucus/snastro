package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.Progetto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AggiungiRegistrazioneServizioTest {
    private val progettoId = ProgettoId("progetto-1")
    private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)
    private val progetti = ProgettoRepositoryFinta().apply {
        salva(Progetto.crea(progettoId, NomeProgetto.di("Consiglio comunale").atteso()).aggregato)
    }
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val generatoreId = GeneratoreIdFinto()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni))
    private val sonda = SondaAudioFinta(
        leggibili = mapOf(SORGENTE to InfoAudio(durataMs = 3_600_000, dataFile = LocalDate.of(2026, 3, 12))),
    )
    private val archivio = ArchivioAudioFinta().apply { conSorgente(SORGENTE) }
    private val servizio = AggiungiRegistrazioneServizio(
        eventi.unitaDiLavoro,
        generatoreId,
        clock,
        progetti,
        registrazioni,
        sonda,
        archivio,
        eventi,
    )

    @Test
    fun `AC-56 un file leggibile crea la Registrazione con titolo, durata e data dalla sonda e pubblica l'evento`() {
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()

        val salvata = assertNotNull(registrazioni.trova(RegistrazioneId("id-1")))
        assertEquals("Seduta del 12 marzo", salvata.titolo)
        assertEquals(3_600_000L, salvata.durataMs)
        assertEquals(LocalDate.of(2026, 3, 12), salvata.dataRegistrazione)
        assertEquals(progettoId, salvata.progettoId)
        assertEquals(
            listOf(RegistrazioneAggiunta(RegistrazioneId("id-1"), progettoId)),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-57 un file illeggibile non crea nulla e non lascia file in audio`() {
        servizio.esegui(AggiungiRegistrazione("/sorgenti/sconosciuto.m4a"))
            .erroreAtteso<ErroreApplicazioneProgetto.AudioNonLeggibile>()

        assertEquals(emptyList(), registrazioni.delProgetto(progettoId))
        assertEquals(emptySet(), archivio.archiviati)
    }

    @Test
    fun `AC-57 un formato non supportato non crea nulla e non lascia file in audio`() {
        val sondaFormato = SondaAudioFinta(nonSupportati = setOf(SORGENTE))
        val servizioFormato = AggiungiRegistrazioneServizio(
            eventi.unitaDiLavoro,
            generatoreId,
            clock,
            progetti,
            registrazioni,
            sondaFormato,
            archivio,
            eventi,
        )

        servizioFormato.esegui(AggiungiRegistrazione(SORGENTE))
            .erroreAtteso<ErroreApplicazioneProgetto.FormatoNonSupportato>()

        assertEquals(emptyList(), registrazioni.delProgetto(progettoId))
        assertEquals(emptySet(), archivio.archiviati)
    }

    @Test
    fun `AC-58 una copia fallita a meta non crea nulla ne una riga ne un file parziale`() {
        val archivioGuasto = ArchivioAudioFinta().apply { conSorgenteCheFallisce(SORGENTE) }
        val servizioGuasto = AggiungiRegistrazioneServizio(
            eventi.unitaDiLavoro,
            generatoreId,
            clock,
            progetti,
            registrazioni,
            sonda,
            archivioGuasto,
            eventi,
        )

        servizioGuasto.esegui(AggiungiRegistrazione(SORGENTE)).erroreAtteso<ErroreApplicazioneProgetto.CopiaFallita>()

        assertEquals(emptyList(), registrazioni.delProgetto(progettoId))
        assertEquals(emptySet(), archivioGuasto.archiviati)
    }

    @Test
    fun `AC-59 il riferimento salvato e quello restituito da ArchivioAudio, relativo alla cartella del progetto`() {
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()

        val salvata = assertNotNull(registrazioni.trova(RegistrazioneId("id-1")))
        assertEquals(RiferimentoAudio("audio/id-1.m4a"), salvata.riferimentoAudio)
        assertEquals(setOf(RiferimentoAudio("audio/id-1.m4a")), archivio.archiviati)
    }

    @Test
    fun `AC-60 se l abbonato sincrono fallisce la Registrazione non esiste e il file copiato e scartato`() {
        eventi.registraSincrono { evento ->
            if (evento is RegistrazioneAggiunta) Esito.Errore(ErroreDiProva.Fallito("coda piena")) else Esito.Ok(Unit)
        }

        servizio.esegui(AggiungiRegistrazione(SORGENTE)).erroreAtteso<ErroreDiProva.Fallito>()

        assertNull(registrazioni.trova(RegistrazioneId("id-1")))
        assertEquals(emptySet(), archivio.archiviati)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-61 aggiungere due volte lo stesso file crea due Registrazioni distinte senza blocchi`() {
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()

        val salvate = registrazioni.delProgetto(progettoId)
        assertEquals(setOf(RegistrazioneId("id-1"), RegistrazioneId("id-2")), salvate.map { it.id }.toSet())
        assertEquals(
            setOf(RiferimentoAudio("audio/id-1.m4a"), RiferimentoAudio("audio/id-2.m4a")),
            archivio.archiviati,
        )
    }

    private companion object {
        const val SORGENTE = "/sorgenti/Seduta del 12 marzo.m4a"
    }
}
