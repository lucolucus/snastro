package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SchemaProgettoNonValidoException
import snastro.persistenza.SchemaProgettoPiuRecenteException
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.ui.ErroreSessione
import snastro.ui.SessioneProgetto
import snastro.ui.SessioneProgettoContratto
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D2 (consumer-driven contract, real-on-real) + the block's own AC-238/239/240/263..265/347/349.
 * No `io.mockk` here (CR-17: this class extends a `*Contratto`) — a mockk-based test (e.g. the H2
 * player-close/scope-cancel one) lives in [SessioneProgettoImplChiudiTest] instead.
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
    ): SessioneProgettoImpl = SessioneProgettoImpl(registro, generatoreId, clock, scopeGenitore = scopeDiProva())

    /** Every test scope is a standalone `SupervisorJob` — nothing outlives one test's own assertions. */
    private fun scopeDiProva(): CoroutineScope = CoroutineScope(SupervisorJob())

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

    // --- fix-batch-12 #2 (DB closed on chiudi/failure paths) -----------------------------------

    @Test
    fun `fix-batch-12 2 chiudi rilascia il database e non lascia file wal aperti`() {
        val sessione = nuovaSessione()
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()
        val cartellaProgetto = Path.of(progetto.percorso)

        sessione.chiudi()

        // DatabaseProgetto.chiudi()'s own `PRAGMA wal_checkpoint(TRUNCATE)` runs synchronously (there
        // is no "last connection closes" auto-checkpoint here — SQLDelight 2.1.0's ThreadedConnectionManager
        // never keeps a connection open long enough to be a last one, its own `close()` is a no-op), but
        // the resulting filesystem state is not guaranteed to be visible the instant the call returns on
        // every OS/filesystem — a bounded poll, same pattern as this file's other eventually-consistent
        // checks (`attendi`), not an immediate assert.
        attendi { Files.notExists(cartellaProgetto.resolve("progetto.db-wal")) }
        attendi { Files.notExists(cartellaProgetto.resolve("progetto.db-shm")) }
    }

    @Test
    fun `fix-batch-12 2 un apri fallito perche il progetto non esiste ancora chiude comunque il database`() {
        // Uno schema valido ma senza alcuna riga progetto: apri arriva fino al ramo `progetto ==
        // null`, dopo aver gia' aperto il database con successo.
        val cartellaProgetto = cartella.resolve("Vuoto.snastro").also(Files::createDirectories)
        apriDatabaseProgetto(cartellaProgetto.toFile()).chiudi()

        val errore = con().apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.CartellaNonValida, errore)
        attendi { Files.notExists(cartellaProgetto.resolve("progetto.db-wal")) }
        attendi { Files.notExists(cartellaProgetto.resolve("progetto.db-shm")) }
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

    // --- fix-batch-12 #6 (registro calls ordered registra -> aggiorna) -------------------------

    /** Records call order; `registra` is deliberately slow so a per-call thread (the pre-fix
     * behaviour) would very likely let the fast `aggiorna` finish first. */
    private class RegistroProgettiCheRegistraLentamente : RegistroProgetti {
        val ordine: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        override fun elenco(): List<VoceRegistro> = emptyList()
        override fun registra(v: VoceRegistro) {
            Thread.sleep(80)
            ordine += "registra"
        }
        override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) {
            ordine += "aggiorna"
        }
        override fun rimuovi(percorso: String) = Unit
    }

    @Test
    fun `fix-batch-12 6 le chiamate al registro restano in ordine registra poi aggiorna anche se registra e lenta`() {
        val registro = RegistroProgettiCheRegistraLentamente()
        val sessione = nuovaSessione(registro = registro)

        sessione.crea(cartella.toString(), "Prova").atteso()
        sessione.chiudi()

        attendi(timeoutMs = 3_000) { registro.ordine.size >= 2 }
        assertEquals(listOf("registra", "aggiorna"), registro.ordine.toList())
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
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = Clock.fixed(ORA, ZoneOffset.UTC),
            scopeGenitore = scopeDiProva(),
            seams = SessioneProgettoSeams(apriDatabase = { throw SchemaProgettoPiuRecenteException(999, 1) }),
        )

        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.DatabasePiuRecente, errore)

        // Il lock non e' rimasto trattenuto: una nuova apri() rifiuta di nuovo per lo stesso motivo
        // (DatabasePiuRecente), mai per ProgettoGiaAperto.
        val secondoErrore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.DatabasePiuRecente, secondoErrore)
        assertNotEquals(ErroreSessione.ProgettoGiaAperto, secondoErrore)
    }

    @Test
    fun `L530f uno schema mai rilasciato (user_version 1) restituisce DatabasePiuRecente, non CartellaNonValida`() {
        // Come sopra: SchemaProgettoNonValidoException e' l'ALTRO sottotipo sigillato di
        // SchemaProgettoRifiutatoException (`:persistenza`'s own AC-12) — anch'esso deve mappare su
        // DatabasePiuRecente, non cadere nel catch generico -> CartellaNonValida.
        val cartellaProgetto = cartella.resolve("MaiRilasciato.snastro").also(Files::createDirectories)
        Files.createFile(cartellaProgetto.resolve("progetto.db"))
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = Clock.fixed(ORA, ZoneOffset.UTC),
            scopeGenitore = scopeDiProva(),
            seams = SessioneProgettoSeams(apriDatabase = { throw SchemaProgettoNonValidoException(1) }),
        )

        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.DatabasePiuRecente, errore)
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
        // fix-batch-12 #2: chiudi() ora chiude anche il database — mai una sessione di test lasciata
        // aperta, o @TempDir puo' trovare un file ancora agganciato quando prova a ripulire.
        seconda.chiudi()
    }

    // --- H3 (AC-349): .lock non trattenuto su eccezione in apertura ------------------------------

    @Test
    fun `H3 un progetto db corrotto restituisce CartellaNonValida e rilascia il lock`() {
        val cartellaProgetto = cartella.resolve("Corrotto.snastro").also(Files::createDirectories)
        // Byte casuali: un file esistente, leggibile, ma non un database SQLite valido — l'apertura
        // reale (apriDatabaseProgetto) lancia un'eccezione SQL leggendo `PRAGMA user_version`.
        Files.write(cartellaProgetto.resolve("progetto.db"), ByteArray(64) { it.toByte() })

        val sessione = con()
        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, errore)

        // Il lock non e' rimasto trattenuto: una seconda apri (ancora corrotta) fallisce di nuovo per
        // lo stesso motivo, mai per ProgettoGiaAperto (prova indiretta del rilascio, come AC-349 sopra).
        val secondoErrore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, secondoErrore)
    }

    @Test
    fun `H3 in crea un errore nell apertura del database rilascia il lock`() {
        val cartellaFallita = cartella.resolve("Prova.snastro") // il primo nome che creaCartellaLibera sceglie
        val sessioneRotta = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = Clock.fixed(ORA, ZoneOffset.UTC),
            scopeGenitore = scopeDiProva(),
            seams = SessioneProgettoSeams(apriDatabase = { throw IllegalStateException("errore di prova") }),
        )

        val errore = sessioneRotta.crea(cartella.toString(), "Prova").erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, errore)

        // Il lock non e' rimasto trattenuto sulla cartella appena creata: prova diretta, si acquisisce
        // `<cartella>/.lock` (se fosse ancora detenuto in questa JVM, tryLock lancerebbe
        // OverlappingFileLockException).
        FileChannel.open(cartellaFallita.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { canale ->
                val lock = assertNotNull(canale.tryLock(), "il .lock e' ancora detenuto dopo il crea fallito")
                lock.release()
            }
    }

    // --- AC-264 race: N thread in gara sullo stesso nome, mai una collisione --------------------

    @Test
    fun `AC-264 race N thread chiamano crea con lo stesso nome, ognuno ottiene una cartella distinta`() {
        val numeroThread = 8
        val via = java.util.concurrent.CountDownLatch(1)
        val pronti = java.util.concurrent.CountDownLatch(numeroThread)
        val eseguibile = Executors.newFixedThreadPool(numeroThread)
        try {
            val futures = (1..numeroThread).map {
                eseguibile.submit<Pair<SessioneProgettoImpl, String>> {
                    pronti.countDown()
                    via.await()
                    val sessione = SessioneProgettoImpl(
                        registro = RegistroProgettiFinta(),
                        generatoreId = GeneratoreIdUuid(),
                        clock = Clock.fixed(ORA, ZoneOffset.UTC),
                        scopeGenitore = scopeDiProva(),
                    )
                    sessione to sessione.crea(cartella.toString(), "Prova").atteso().percorso
                }
            }
            assertTrue(pronti.await(5, TimeUnit.SECONDS))
            via.countDown()
            val risultati = futures.map { it.get(10, TimeUnit.SECONDS) }
            // chiudi ogni sessione: rilascia i .lock e le connessioni SQLite aperte prima che @TempDir
            // provi a ripulire la cartella condivisa a fine test.
            risultati.forEach { (sessione, _) -> sessione.chiudi() }
            val percorsi = risultati.map { it.second }

            assertEquals(numeroThread, percorsi.toSet().size, "ogni thread deve ottenere una cartella distinta")
            percorsi.forEach { assertTrue(Files.isDirectory(Path.of(it)), "$it deve esistere") }
        } finally {
            eseguibile.shutdownNow()
        }
    }

    // --- L2 (ADR 0010) ----------------------------------------------------------------------------

    @Test
    fun `L2 crea crea la cartella genitore se non esiste ancora`() {
        val genitoreMancante = cartella.resolve("Documents/snastro")
        assertTrue(Files.notExists(genitoreMancante))

        val progetto = con().crea(genitoreMancante.toString(), "Prova").atteso()

        assertTrue(Files.isDirectory(genitoreMancante))
        assertTrue(Files.isDirectory(Path.of(progetto.percorso)))
    }

    private companion object {
        val ORA: Instant = Instant.parse("2026-01-01T10:00:00Z")
    }
}
