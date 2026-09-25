package snastro.progetto.dominio

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.LocalDate
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
}
