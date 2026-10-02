package snastro.progetto.dominio

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrazioneTest {
    private val id = RegistrazioneId("id-1")
    private val progettoId = ProgettoId("id-2")
    private val dataFile = LocalDate.of(2026, 9, 12)
    private val dataScelta = LocalDate.of(2026, 9, 10)

    private fun unaRegistrazione(dataRegistrazione: LocalDate = dataFile) =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            incontroId = IncontroId("incontro-di-${id.valore}"),
            titolo = "Intervista Marco",
            riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
            durataMs = 3_600_000L,
            dataRegistrazione = dataRegistrazione,
            aggiuntaAlle = Instant.parse("2026-09-23T10:00:00Z"),
        )

    @Test
    fun `INV-1 progettoId e fissato da aggiungi e nessun metodo lo cambia`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.modificaData(dataScelta).atteso()

        assertEquals(progettoId, registrazione.progettoId)
        val campo = Registrazione::class.java.getDeclaredField("progettoId")
        assertTrue(Modifier.isFinal(campo.modifiers), "progettoId deve essere immutabile")
    }

    @Test
    fun `INV-2 aggiungi fissa la data data dal file`() {
        assertEquals(dataFile, unaRegistrazione().aggregato.dataRegistrazione)
    }

    @Test
    fun `INV-2 modificaData sostituisce la data ed emette DataRegistrazioneModificata con precedente e nuova`() {
        val registrazione = unaRegistrazione().aggregato

        val evento = registrazione.modificaData(dataScelta).atteso()

        assertEquals(DataRegistrazioneModificata(id, precedente = dataFile, nuova = dataScelta), evento)
        assertEquals(dataScelta, registrazione.dataRegistrazione)
    }

    @Test
    fun `aggiungi restituisce l'aggregato con i dati dati e l'evento RegistrazioneAggiunta`() {
        val creato = unaRegistrazione()
        val registrazione = creato.aggregato

        assertEquals(id, registrazione.id)
        assertEquals(progettoId, registrazione.progettoId)
        assertEquals(RiferimentoAudio("audio/id-1.m4a"), registrazione.riferimentoAudio)
        assertEquals(3_600_000L, registrazione.durataMs)
        assertEquals(Instant.parse("2026-09-23T10:00:00Z"), registrazione.aggiuntaAlle)
        assertEquals(RegistrazioneAggiunta(id, progettoId), creato.evento)
    }

    @Test
    fun `INV-I2 aggiungi tiene aggiuntaAlle al millisecondo come la persistenza, cosi l ordine non cambia dopo`() {
        val conNanosecondi = Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            incontroId = IncontroId("incontro-di-${'$'}{id.valore}"),
            titolo = "Intervista Marco",
            riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
            durataMs = 1L,
            dataRegistrazione = dataFile,
            aggiuntaAlle = Instant.parse("2026-09-23T10:00:00.123999999Z"),
        ).aggregato

        assertEquals(Instant.parse("2026-09-23T10:00:00.123Z"), conNanosecondi.aggiuntaAlle)
    }

    @Test
    fun `AC-18 il titolo resta quello dato ad aggiungi dopo modificaData`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.modificaData(dataScelta).atteso()

        assertEquals("Intervista Marco", registrazione.titolo)
    }

    // --- AC-360: rinomina -----------------------------------------------------------------------

    @Test
    fun `AC-360 rinomina cambia il titolo ed emette RegistrazioneRinominata con precedente e nuovo`() {
        val registrazione = unaRegistrazione().aggregato

        val evento = registrazione.rinomina("Intervista a Marco Rossi").atteso()

        assertEquals(RegistrazioneRinominata(id, "Intervista Marco", "Intervista a Marco Rossi"), evento)
        assertEquals("Intervista a Marco Rossi", registrazione.titolo)
    }

    @Test
    fun `AC-360 rinomina toglie gli spazi iniziali e finali`() {
        val registrazione = unaRegistrazione().aggregato

        val evento = registrazione.rinomina("  Seduta di marzo \t").atteso()

        assertEquals("Seduta di marzo", evento?.nuovo)
        assertEquals("Seduta di marzo", registrazione.titolo)
    }

    @Test
    fun `AC-360 un titolo vuoto o di soli spazi restituisce TitoloVuoto e il titolo resta`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.rinomina("").erroreAtteso<ErroreProgetto.TitoloVuoto>()
        registrazione.rinomina("   ").erroreAtteso<ErroreProgetto.TitoloVuoto>()

        assertEquals("Intervista Marco", registrazione.titolo)
    }

    @Test
    fun `AC-360 lo stesso titolo, anche con spazi attorno, e un no-op senza evento`() {
        val registrazione = unaRegistrazione().aggregato

        assertNull(registrazione.rinomina("Intervista Marco").atteso())
        assertNull(registrazione.rinomina(" Intervista Marco ").atteso())
        assertEquals("Intervista Marco", registrazione.titolo)
    }

    @Test
    fun `AC-362 rinomina non tocca il riferimento audio ne la data`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.rinomina("Altro titolo").atteso()

        assertEquals(RiferimentoAudio("audio/id-1.m4a"), registrazione.riferimentoAudio)
        assertEquals(dataFile, registrazione.dataRegistrazione)
    }

    // --- AC-613: elimina (ADR 0020) -------------------------------------------------------------

    @Test
    fun `AC-613 elimina restituisce RegistrazioneEliminata con titolo e data correnti e non cambia lo stato`() {
        val registrazione = unaRegistrazione().aggregato
        registrazione.rinomina("Seduta di marzo").atteso()
        registrazione.modificaData(dataScelta).atteso()

        val evento = registrazione.elimina()

        assertEquals(
            RegistrazioneEliminata(
                id = id,
                progettoId = progettoId,
                titolo = "Seduta di marzo",
                dataRegistrazione = dataScelta,
                riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
            ),
            evento,
        )
        assertEquals(evento, registrazione.elimina(), "puro: ripetibile, stesso evento")
        assertEquals("Seduta di marzo", registrazione.titolo)
        assertEquals(dataScelta, registrazione.dataRegistrazione)
        assertEquals(progettoId, registrazione.progettoId)
    }

    // --- ADR 0033 §1: incontroId (INV-I1) and OraDiInizio (AC-I14) -------------------------------

    private val incontroId = IncontroId("incontro-di-id-1")

    private fun ora(testo: String): OraDiInizio = OraDiInizio.di(LocalTime.parse(testo)).atteso()

    @Test
    fun `INV-I1 incontroId e fissato da aggiungi e nessun metodo mutante lo cambia`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.rinomina("Altro titolo").atteso()
        registrazione.modificaData(dataScelta).atteso()
        registrazione.modificaOraDiInizio(ora("10:25:00")).atteso()
        registrazione.modificaOraDiInizio(null).atteso()
        registrazione.elimina()

        assertEquals(incontroId, registrazione.incontroId)
        val campo = Registrazione::class.java.getDeclaredField("incontroId")
        assertTrue(Modifier.isFinal(campo.modifiers), "incontroId deve essere immutabile")
        val setter = Registrazione::class.java.methods.filter { it.name.startsWith("set") }
        assertEquals(emptyList(), setter.map { it.name }, "nessun setter pubblico")
    }

    @Test
    fun `INV-I14 aggiungi senza ora lascia l ora di inizio vuota, con un ora la fissa`() {
        assertNull(unaRegistrazione().aggregato.oraDiInizio)

        val conOra = Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            incontroId = incontroId,
            titolo = "Intervista Marco",
            riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
            durataMs = 1L,
            dataRegistrazione = dataFile,
            aggiuntaAlle = Instant.EPOCH,
            oraDiInizio = ora("09:00:00"),
        ).aggregato

        assertEquals(ora("09:00:00"), conOra.oraDiInizio)
    }

    @Test
    fun `AC-I14 modificaOraDiInizio fissa la nuova ora ed emette OraDiInizioModificata con precedente e nuova`() {
        val registrazione = unaRegistrazione().aggregato

        val evento = registrazione.modificaOraDiInizio(ora("10:25:00")).atteso()

        assertEquals(OraDiInizioModificataDominio(id, incontroId, precedente = null, nuova = ora("10:25:00")), evento)
        assertEquals(ora("10:25:00"), registrazione.oraDiInizio)
    }

    @Test
    fun `AC-I14 la stessa ora, anche vuota su vuota, non emette evento`() {
        val registrazione = unaRegistrazione().aggregato

        assertNull(registrazione.modificaOraDiInizio(null).atteso())
        registrazione.modificaOraDiInizio(ora("10:25:00")).atteso()
        assertNull(registrazione.modificaOraDiInizio(ora("10:25:00")).atteso())
        assertEquals(ora("10:25:00"), registrazione.oraDiInizio)
    }

    @Test
    fun `AC-I14 togliere un ora fissata e ammesso ed emette l evento`() {
        val registrazione = unaRegistrazione().aggregato
        registrazione.modificaOraDiInizio(ora("10:25:00")).atteso()

        val evento = registrazione.modificaOraDiInizio(null).atteso()

        assertEquals(OraDiInizioModificataDominio(id, incontroId, precedente = ora("10:25:00"), nuova = null), evento)
        assertNull(registrazione.oraDiInizio)
    }
}
