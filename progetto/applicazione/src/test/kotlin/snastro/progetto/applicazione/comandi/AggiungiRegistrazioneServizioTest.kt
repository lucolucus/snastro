package snastro.progetto.applicazione.comandi

import org.junit.jupiter.api.Timeout
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
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
import snastro.progetto.dominio.Registrazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
    fun `AC-60 se l abbonato sincrono lancia un eccezione la Registrazione non esiste e il file copiato e scartato`() {
        eventi.registraSincrono { evento ->
            if (evento is RegistrazioneAggiunta) throw GuastoDiProva() else Esito.Ok(Unit)
        }

        assertFailsWith<GuastoDiProva> { servizio.esegui(AggiungiRegistrazione(SORGENTE)) }

        assertNull(registrazioni.trova(RegistrazioneId("id-1")))
        assertEquals(emptySet(), archivio.archiviati)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-61 aggiungere due volte lo stesso file crea due Registrazioni distinte, la seconda con titolo (2)`() {
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()
        servizio.esegui(AggiungiRegistrazione(SORGENTE)).atteso()

        val salvate = registrazioni.delProgetto(progettoId)
        assertEquals(setOf(RegistrazioneId("id-1"), RegistrazioneId("id-2")), salvate.map { it.id }.toSet())
        assertEquals("Seduta del 12 marzo", assertNotNull(registrazioni.trova(RegistrazioneId("id-1"))).titolo)
        assertEquals("Seduta del 12 marzo (2)", assertNotNull(registrazioni.trova(RegistrazioneId("id-2"))).titolo)
        assertEquals(
            setOf(RiferimentoAudio("audio/id-1.m4a"), RiferimentoAudio("audio/id-2.m4a")),
            archivio.archiviati,
        )
    }

    @Test
    fun `AC-322 un titolo con la stessa chiave nel Progetto riceve il primo suffisso libero`() {
        conRegistrazione("Riunione")
        assertEquals("riunione (2)", titoloAggiunto("/sorgenti/riunione.m4a"))

        conRegistrazione("Altra")
        assertEquals("Riunione (3)", titoloAggiunto("/sorgenti/Riunione.wav"))
    }

    @Test
    fun `AC-322 la chiave confronta i titoli puliti, Riunione con asterisco e con punto interrogativo collidono`() {
        conRegistrazione("Riunione*")

        assertEquals("Riunione? (2)", titoloAggiunto("/sorgenti/Riunione?.m4a"))
    }

    @Test
    fun `AC-322 la base e' il nome del file in NFC e senza spazi ai bordi`() {
        conRegistrazione("\u00e9")

        assertEquals("\u00e9 (2)", titoloAggiunto("/sorgenti/ e\u0301 .m4a"))
    }

    @Test
    fun `AC-323 i titoli di un altro Progetto non contano`() {
        conRegistrazione("Riunione", ProgettoId("altro-progetto"))

        assertEquals("Riunione", titoloAggiunto("/sorgenti/Riunione.m4a"))
    }

    @Test
    fun `AC-323 stessi titoli esistenti e stesso file danno lo stesso titolo, e il titolo assegnato non cambia piu'`() {
        val esistente = conRegistrazione("Riunione")
        val primo = titoloAggiunto("/sorgenti/Riunione.m4a")

        val altroRepository = RegistrazioneRepositoryFinta()
        altroRepository.salva(unaRegistrazione("Riunione", progettoId, esistente))
        val secondo = titoloPer("/sorgenti/Riunione.m4a", altroRepository)

        assertEquals("Riunione (2)", primo)
        assertEquals(primo, secondo)
        assertEquals("Riunione", assertNotNull(registrazioni.trova(esistente)).titolo)

        ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
            .esegui(ModificaDataRegistrazione(RegistrazioneId("id-1"), LocalDate.of(2026, 1, 2))).atteso()
        assertEquals("Riunione (2)", assertNotNull(registrazioni.trova(RegistrazioneId("id-1"))).titolo)
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // a lost suffix never terminates
    fun `AC-324 un nome lungo e' troncato prima del suffisso e le due chiavi differiscono`() {
        val primi237 = "x".repeat(237)
        val esistente = primi237 + "y".repeat(13) // 250 byte
        conRegistrazione(esistente)

        val titolo = titoloAggiunto("/sorgenti/${primi237}zzzzz.m4a")

        assertEquals("x".repeat(233) + " (2)", titolo)
        assertNotEquals(TitoloRegistrazione.chiave(esistente), TitoloRegistrazione.chiave(titolo))
    }

    @Test
    fun `AC-56 titoloDa vari percorsi sorgente`() {
        val casi = listOf(
            CasoTitolo(
                "percorso Windows con backslash",
                "C:\\Users\\foo\\Seduta del 12 marzo.m4a",
                "Seduta del 12 marzo",
            ),
            CasoTitolo("percorso senza estensione", "/sorgenti/Seduta", "Seduta"),
            CasoTitolo("dotfile: l'estensione e' l'intero nome", "/sorgenti/.m4a", ".m4a"),
            CasoTitolo("separatore finale: nessun nome file", "/sorgenti/dir/", "registrazione"),
        )

        casi.forEach { caso -> assertEquals(caso.atteso, titoloPer(caso.percorso), caso.descrizione) }
    }

    /** Seeds [registrazioni] with an existing Registrazione titled [titolo] (of [progetto], default this one). */
    private fun conRegistrazione(titolo: String, progetto: ProgettoId = progettoId): RegistrazioneId {
        val id = RegistrazioneId("esistente-${registrazioni.delProgetto(progetto).size + 1}-${progetto.valore}")
        registrazioni.salva(unaRegistrazione(titolo, progetto, id))
        return id
    }

    private fun unaRegistrazione(titolo: String, progetto: ProgettoId, id: RegistrazioneId): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progetto,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 1_000L,
            dataRegistrazione = LocalDate.of(2026, 1, 1),
            aggiuntaAlle = Instant.parse("2026-01-01T00:00:00Z"),
        ).aggregato

    /** Runs AggiungiRegistrazione for [percorsoSorgente] on this test's [registrazioni]; returns the new titolo. */
    private fun titoloAggiunto(percorsoSorgente: String): String =
        titoloPer(percorsoSorgente, registrazioni, generatoreId)

    /**
     * Runs AggiungiRegistrazione on a fresh service/fakes over [registrazioniLocali] (default: an empty
     * repository) for [percorsoSorgente] and returns the saved titolo of the new Registrazione.
     */
    private fun titoloPer(
        percorsoSorgente: String,
        registrazioniLocali: RegistrazioneRepositoryFinta = RegistrazioneRepositoryFinta(),
        generatoreLocale: GeneratoreId = GeneratoreIdFinto(),
    ): String {
        val prima = registrazioniLocali.delProgetto(progettoId).map { it.id }.toSet()
        val eventiLocali = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniLocali))
        val progettiLocali = ProgettoRepositoryFinta().apply {
            salva(Progetto.crea(progettoId, NomeProgetto.di("Consiglio comunale").atteso()).aggregato)
        }
        val servizioLocale = AggiungiRegistrazioneServizio(
            eventiLocali.unitaDiLavoro,
            generatoreLocale,
            clock,
            progettiLocali,
            registrazioniLocali,
            SondaAudioFinta(
                leggibili = mapOf(
                    percorsoSorgente to InfoAudio(durataMs = 1_000L, dataFile = LocalDate.of(2026, 1, 1)),
                ),
            ),
            ArchivioAudioFinta().apply { conSorgente(percorsoSorgente) },
            eventiLocali,
        )

        servizioLocale.esegui(AggiungiRegistrazione(percorsoSorgente)).atteso()

        return registrazioniLocali.delProgetto(progettoId).single { it.id !in prima }.titolo
    }

    private class GuastoDiProva : RuntimeException("guasto di prova")

    private data class CasoTitolo(val descrizione: String, val percorso: String, val atteso: String)

    private companion object {
        const val SORGENTE = "/sorgenti/Seduta del 12 marzo.m4a"
    }
}
