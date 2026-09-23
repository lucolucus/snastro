package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SchemaProgettoPiuRecenteException
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.ui.ErroreSessione
import snastro.ui.SessioneProgetto
import snastro.ui.SessioneProgettoContratto
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * D2 (consumer-driven contract, real-on-real) + the block's own AC-238/239/240/263..265/347/349.
 */
class SessioneProgettoImplTest : SessioneProgettoContratto() {
    @TempDir
    lateinit var cartella: Path

    override fun con(): SessioneProgetto = nuovaSessione()

    override fun cartellaGenitoreProva(): String = cartella.toString()

    private fun nuovaSessione(
        registro: RegistroProgetti = RegistroProgettiFinta(),
        generatoreId: GeneratoreIdFinto = GeneratoreIdFinto(),
        clock: Clock = Clock.fixed(ORA, ZoneOffset.UTC),
    ): SessioneProgettoImpl = SessioneProgettoImpl(registro, generatoreId, clock)

    private fun attendi(timeoutMs: Long = 2_000, condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < scadenza && !condizione()) Thread.sleep(10)
        assertTrue(condizione(), "condizione non soddisfatta entro ${timeoutMs}ms")
    }

    // --- AC-239 ------------------------------------------------------------------------------

    @Test
    fun `AC-239 crea crea la struttura di cartelle progetto db e lock, e registra il percorso assoluto`() {
        val registro = RegistroProgettiFinta()
        val sessione = nuovaSessione(registro = registro)

        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()

        val cartellaProgetto = Path.of(progetto.percorso)
        assertTrue(Files.isDirectory(cartellaProgetto.resolve("audio")))
        assertTrue(Files.isDirectory(cartellaProgetto.resolve("documenti")))
        assertTrue(Files.isDirectory(cartellaProgetto.resolve("cache/audio")))
        assertTrue(Files.isRegularFile(cartellaProgetto.resolve("progetto.db")))
        assertTrue(Files.exists(cartellaProgetto.resolve(".lock")))
        assertEquals(cartellaProgetto.toAbsolutePath().normalize().toString(), progetto.percorso)
        attendi { registro.elenco().any { it.percorso == progetto.percorso } }
    }

    // --- AC-263/AC-264 -------------------------------------------------------------------------

    @Test
    fun `AC-263 il nome della cartella deriva da NomeProgetto`() {
        val progetto = con().crea(cartella.toString(), "Riunione 3/10: budget?").atteso()
        assertEquals(cartella.resolve("Riunione 3_10_ budget_.snastro").toString(), progetto.percorso)
    }

    @Test
    fun `AC-264 una collisione di cartella usa il primo numero libero, mai un errore o una sovrascrittura`() {
        val primo = nuovaSessione().crea(cartella.toString(), "Prova").atteso()
        val contenutoPreesistente = "contenuto preesistente".toByteArray()
        Files.write(Path.of(primo.percorso).resolve("audio/marker.txt"), contenutoPreesistente)

        val secondo = nuovaSessione().crea(cartella.toString(), "Prova").atteso()

        assertEquals(cartella.resolve("Prova.snastro").toString(), primo.percorso)
        assertEquals(cartella.resolve("Prova (2).snastro").toString(), secondo.percorso)
        val marker = Files.readAllBytes(Path.of(primo.percorso).resolve("audio/marker.txt"))
        assertContentEquals(contenutoPreesistente, marker)
    }

    // --- AC-265 ----------------------------------------------------------------------------------

    @Test
    fun `AC-265 il Progetto mantiene il nome digitato, il percorso usa il nome di cartella derivato`() {
        val progetto = con().crea(cartella.toString(), "Riunione 3/10: budget?").atteso()
        assertEquals("Riunione 3/10: budget?", progetto.nome)
        assertTrue(progetto.percorso.endsWith("Riunione 3_10_ budget_.snastro"))
    }

    @Test
    fun `AC-265 un nome vuoto o di soli spazi non crea alcuna cartella`() {
        val vociPrima = Files.newDirectoryStream(cartella).use { it.count() }

        val errore = con().crea(cartella.toString(), "   ").erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.NomeProgettoVuoto, errore)
        val vociDopo = Files.newDirectoryStream(cartella).use { it.count() }
        assertEquals(vociPrima, vociDopo)
    }

    // --- AC-238 ------------------------------------------------------------------------------

    @Test
    fun `AC-238 una seconda istanza sullo stesso progetto riceve progetto gia aperto`() {
        val progetto = con().crea(cartella.toString(), "Prova").atteso()

        val errore = nuovaSessione().apri(progetto.percorso).erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.ProgettoGiaAperto, errore)
    }

    // --- AC-240 ------------------------------------------------------------------------------

    @Test
    fun `AC-240 alla chiusura il registro e aggiornato con numRegistrazioni e ultimaAttivita`() {
        val registro = RegistroProgettiFinta()
        val sessione = nuovaSessione(registro = registro)
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()
        attendi { registro.elenco().any { it.percorso == progetto.percorso } } // la registra di crea

        sessione.chiudi()

        attendi { registro.elenco().firstOrNull { it.percorso == progetto.percorso }?.ultimaAttivita == ORA }
        val voce = registro.elenco().first { it.percorso == progetto.percorso }
        assertEquals(0, voce.numRegistrazioni)
    }

    // --- AC-347 ------------------------------------------------------------------------------

    private class RegistroProgettiCheLanciaSempre : RegistroProgetti {
        override fun elenco(): List<VoceRegistro> = error("registro rotto")
        override fun registra(v: VoceRegistro): Unit = error("registro rotto")
        override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant): Unit =
            error("registro rotto")
        override fun rimuovi(percorso: String): Unit = error("registro rotto")
    }

    @Test
    fun `AC-347 un RegistroProgetti che lancia sempre non impedisce crea apri chiudi, e rilascia comunque il lock`() {
        val registroRotto = RegistroProgettiCheLanciaSempre()

        val prima = nuovaSessione(registro = registroRotto)
        val progetto = prima.crea(cartella.toString(), "Prova").atteso() // Ok nonostante il registro rotto
        prima.chiudi() // non lancia, e rilascia il lock

        val seconda = nuovaSessione(registro = registroRotto)
        val riaperto = seconda.apri(progetto.percorso).atteso() // il lock e' stato rilasciato
        assertEquals(progetto.progettoId, riaperto.progettoId)
        seconda.chiudi()
    }

    // --- AC-349 ------------------------------------------------------------------------------

    @Test
    fun `AC-349 aprire una cartella senza progetto db restituisce CartellaNonValida`() {
        val vuota = cartella.resolve("vuota").also(Files::createDirectories)

        val errore = con().apri(vuota.toString()).erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.CartellaNonValida, errore)
    }

    @Test
    fun `AC-349 aprire un percorso inesistente restituisce CartellaNonValida`() {
        val errore = con().apri(cartella.resolve("non-esiste").toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, errore)
    }

    @Test
    fun `AC-349 uno schema piu recente restituisce DatabasePiuRecente, senza lasciare il lock`() {
        // Il DB stesso e' una preoccupazione di `:persistenza` (gia' provata dal suo AC-12): questo
        // blocco prova solo che SessioneProgettoImpl mappa lo SchemaProgettoPiuRecenteException di
        // apriDatabase in ErroreSessione.DatabasePiuRecente e rilascia il lock — via il seam iniettabile
        // (mai import diretti di sqldelight/sqlite da `:avvio`, CR-3).
        val cartellaProgetto = cartella.resolve("Futuro.snastro").also(Files::createDirectories)
        Files.createFile(cartellaProgetto.resolve("progetto.db"))
        val sessione = SessioneProgettoImpl(
            RegistroProgettiFinta(),
            GeneratoreIdFinto(),
            Clock.fixed(ORA, ZoneOffset.UTC),
        ) { throw SchemaProgettoPiuRecenteException(999, 1) }

        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.DatabasePiuRecente, errore)

        // Il lock non e' rimasto trattenuto: una nuova apri() rifiuta di nuovo per lo stesso motivo
        // (DatabasePiuRecente), mai per ProgettoGiaAperto.
        val secondoErrore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.DatabasePiuRecente, secondoErrore)
        assertNotEquals(ErroreSessione.ProgettoGiaAperto, secondoErrore)
    }

    @Test
    fun `AC-349 un progetto spostato viene aperto e ri-registrato con il nuovo percorso assoluto`() {
        val registro = RegistroProgettiFinta()
        val sessione = nuovaSessione(registro = registro)
        val creato = sessione.crea(cartella.toString(), "Prova").atteso()
        sessione.chiudi()

        // Dentro la stessa @TempDir (mai una sorella del tempdir radice: un nome letterale li'
        // potrebbe collidere con residui di un'altra esecuzione di questo test).
        val nuovaPosizione = cartella.resolve("spostato")
        Files.move(Path.of(creato.percorso), nuovaPosizione)

        val riaperto = sessione.apri(nuovaPosizione.toString()).atteso()

        assertEquals(nuovaPosizione.toAbsolutePath().normalize().toString(), riaperto.percorso)
        assertEquals(creato.progettoId, riaperto.progettoId)
        attendi { registro.elenco().any { it.percorso == riaperto.percorso } }
    }

    @Test
    fun `AC-349 chiudi rilascia il lock cosi che un altra istanza possa riaprire`() {
        val prima = con()
        val progetto = prima.crea(cartella.toString(), "Prova").atteso()

        prima.chiudi()

        val seconda = nuovaSessione()
        val riaperto = seconda.apri(progetto.percorso).atteso()
        assertEquals(progetto.progettoId, riaperto.progettoId)
    }

    private companion object {
        val ORA: Instant = Instant.parse("2026-01-01T10:00:00Z")
    }
}
