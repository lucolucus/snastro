package snastro.progetto.dominio

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // AC-18 is by construction (no method changes titolo); this test only documents it, it is not coverage.
    @Test
    fun `AC-18 il titolo resta quello dato ad aggiungi dopo modificaData`() {
        val registrazione = unaRegistrazione().aggregato

        registrazione.modificaData(dataScelta).atteso()

        assertEquals("Intervista Marco", registrazione.titolo)
    }
}
