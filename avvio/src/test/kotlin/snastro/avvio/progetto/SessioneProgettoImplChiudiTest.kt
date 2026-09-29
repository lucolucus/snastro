package snastro.avvio.progetto

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import snastro.audio.RiproduttoreWav
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.dominio.Registrazione
import snastro.supporto.test.attendiFinche
import snastro.ui.ErroreSessione
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * H2 (audio keeps playing / presenter collectors leak after "Chiudi progetto") + H3 (`.lock` not
 * released when `delProgetto` throws inside `chiudi`). MockK verifies interactions on
 * [RiproduttoreWav] (RC-9: no hand-written recording fake exists for it — it is not a port) and a
 * throwing [RegistrazioneRepository] wrapper proves the `finally` cleanup (RC-9 prefers a
 * hand-written fake over MockK where a fake already exists, `RegistrazioneRepository` has one). This
 * class does NOT extend a `*Contratto` — `io.mockk` is off-limits there (CR-17); see
 * [SessioneProgettoImplTest] for the rest of the block's own D2 contract + ACs.
 */
class SessioneProgettoImplChiudiTest {
    /** Every session a test opened: closed after it (their queue and workers must not outlive the @TempDir). */
    private val sessioni = CopyOnWriteArrayList<SessioneProgettoImpl>()

    @AfterEach
    fun chiudiLeSessioni() {
        sessioni.forEach(SessioneProgettoImpl::chiudi)
    }

    @TempDir
    lateinit var cartella: Path

    private val orologio: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)

    private fun scopeDiProva(): CoroutineScope = CoroutineScope(SupervisorJob())

    /** A session with no seam of its own (a reopen, another instance), closed after the test. */
    private fun sessioneSemplice(): SessioneProgettoImpl {
        val app = componentiDiProva()
        return SessioneProgettoImpl(RegistroProgettiFinta(), GeneratoreIdFinto(), orologio, scopeDiProva(), app)
            .also { sessioni += it }
    }

    @Test
    fun `H2 chiudi cancella lo scope di sessione e chiude il lettore reale`() {
        val riproduttoreFinto = mockk<RiproduttoreWav>(relaxed = true)
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(riproduttoreFabbrica = { riproduttoreFinto }),
        ).also(sessioni::add)
        sessione.crea(cartella.toString(), "Prova").atteso()
        val scopeSessione = sessione.collaboratoriCorrenti()!!.scope
        assertTrue(scopeSessione.isActive)

        sessione.chiudi()

        assertFalse(scopeSessione.isActive, "lo scope della sessione deve essere cancellato da chiudi")
        verify { riproduttoreFinto.close() }
    }

    @Test
    fun `H2 riaprire dopo chiudi crea un lettore nuovo, mai due lettori attivi insieme`() {
        val riproduttori = mutableListOf<RiproduttoreWav>()
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(
                riproduttoreFabbrica = { mockk<RiproduttoreWav>(relaxed = true).also { riproduttori += it } },
            ),
        ).also(sessioni::add)

        sessione.crea(cartella.toString(), "Prova").atteso()
        sessione.chiudi()
        sessione.crea(cartella.toString(), "Prova").atteso()

        assertEquals(2, riproduttori.size, "ogni apertura deve costruire il suo proprio riproduttore")
        verify { riproduttori[0].close() } // il primo e' stato chiuso da chiudi()
        verify(exactly = 0) { riproduttori[1].close() } // il secondo e' ancora aperto: mai chiuso in anticipo
    }

    @Test
    fun `H3 chiudi rilascia il lock e azzera corrente anche se la lettura delle Registrazioni lancia`() {
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(
                costruisciRegistrazioni = { db -> RegistrazioneRepositoryCheLancia(RegistrazioneRepositorySql(db)) },
            ),
        ).also(sessioni::add)
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()

        sessione.chiudi() // non deve lanciare, nonostante delProgetto rotto (stessa regola AC-347)

        assertNull(sessione.corrente.value)
        val riaperta = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
        ).also(sessioni::add)
        val riaperto = riaperta.apri(progetto.percorso).atteso() // il lock e' stato rilasciato
        assertEquals(progetto.progettoId, riaperto.progettoId)
    }

    private class RegistrazioneRepositoryCheLancia(
        private val delegato: RegistrazioneRepository,
    ) : RegistrazioneRepository by delegato {
        override fun delProgetto(id: ProgettoId): List<Registrazione> = error("repository rotto")
    }

    // --- fix-batch-13 (chiudiDb che lancia in chiudi/crea/apri) ---------------------------------

    @Test
    fun `fix-batch-13 chiudi non lancia ne trattiene lock, scope e corrente anche se chiudiDb lancia`() {
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(chiudiDatabase = { throw IllegalStateException("checkpoint fallito") }),
        ).also(sessioni::add)
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()
        val scopeSessione = sessione.collaboratoriCorrenti()!!.scope
        assertTrue(scopeSessione.isActive)

        sessione.chiudi() // non deve lanciare, nonostante chiudiDb rotto (stessa regola AC-347)

        assertFalse(scopeSessione.isActive, "lo scope della sessione deve essere cancellato anche se chiudiDb lancia")
        assertNull(sessione.corrente.value)
        val riaperta = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
        ).also(sessioni::add)
        val riaperto = riaperta.apri(progetto.percorso).atteso() // il lock e' stato rilasciato
        assertEquals(progetto.progettoId, riaperto.progettoId)
        riaperta.chiudi()
    }

    @Test
    fun `fix-batch-13 chiudiDb che lancia in apri progetto assente non maschera l errore ne trattiene il lock`() {
        val cartellaProgetto = cartella.resolve("Vuoto.snastro").also(Files::createDirectories)
        apriDatabaseProgetto(cartellaProgetto.toFile()).chiudi() // schema valido, nessuna riga progetto
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(chiudiDatabase = { throw IllegalStateException("checkpoint fallito") }),
        ).also(sessioni::add)

        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.CartellaNonValida, errore)
        // il lock non e' rimasto trattenuto: una seconda apri fallisce di nuovo per lo stesso motivo,
        // mai per ProgettoGiaAperto.
        val secondoErrore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, secondoErrore)
    }

    // --- carry-over 1: the composition's stop (ArrestoProgetto) sits between scope cancel and db close --------

    @Test
    fun `carry-over 1 chiudi ferma lettore, scope, lavori del progetto, poi chiude il database e rilascia il lock`() {
        val ordine = CopyOnWriteArrayList<String>()
        val riproduttore = mockk<RiproduttoreWav>(relaxed = true) { every { close() } answers { ordine += "lettore" } }
        lateinit var sessione: SessioneProgettoImpl
        lateinit var composto: ProgettoComposto
        sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(),
            seams = SessioneProgettoSeams(
                riproduttoreFabbrica = { riproduttore },
                chiudiDatabase = { db ->
                    val cartella = Path.of(checkNotNull(ultimoPercorso))
                    ordine += "database (lock tenuto: ${lockTenuto(cartella)}, " +
                        "scope attivo: ${composto.collaboratori.scope.isActive}, " +
                        "coda ferma: ${composto.coda.lavoro.isCompleted})"
                    db.chiudi()
                },
            ),
        ).also(sessioni::add)
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()
        ultimoPercorso = progetto.percorso
        composto = checkNotNull(sessione.progettoCorrente())

        sessione.chiudi()

        assertEquals(
            listOf("lettore", "database (lock tenuto: true, scope attivo: false, coda ferma: true)"),
            ordine.toList(),
        )
        assertFalse(lockTenuto(Path.of(progetto.percorso)), "il lock va rilasciato dopo la chiusura del database")
        val riaperta = sessioneSemplice()
        riaperta.apri(progetto.percorso).atteso()
        riaperta.chiudi()
    }

    // fix-batch-16 MED-1(b) (never the database closed nor the lock released under a live worker): the deferral itself
    // is `ArrestoProgettoTest`'s; on the real composition, with a pipeline stuck in a native call,
    // `ChiusuraProgettoTest`.

    @Test
    fun `una composizione che fallisce in costruzione non trattiene lock, database ne scope`() {
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            app = componentiDiProva(adattatoriMl = { error("adattatori ML rotti") }),
        ).also(sessioni::add)

        val errore = sessione.crea(cartella.toString(), "Prova").erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.CartellaNonValida, errore)
        assertNull(sessione.corrente.value)
        val riaperta = sessioneSemplice()
        riaperta.apri(cartella.resolve("Prova.snastro").toString()).atteso() // lock rilasciato, db leggibile
        riaperta.chiudi()
    }

    // --- AC-C55/AC-C56 (ADR 0028 §2, figlioDi + gestoreErroriNonCatturati) ----------------------------

    @Test
    fun `AC-C55 AC-C56 un errore non catturato per-progetto e segnalato una volta, scope e fratelli sopravvivono`() {
        val catturati = CopyOnWriteArrayList<LogRecord>()
        val spia = object : Handler() {
            override fun publish(record: LogRecord) {
                if (record.thrown?.message == "guasto iniettato") catturati += record
            }

            override fun flush() = Unit
            override fun close() = Unit
        }
        val logger = Logger.getLogger("snastro").apply { addHandler(spia) }
        try {
            val fratelloAttendeAncora = CompletableDeferred<Unit>()
            val sessione = SessioneProgettoImpl(
                registro = RegistroProgettiFinta(),
                generatoreId = GeneratoreIdFinto(),
                clock = orologio,
                scopeGenitore = scopeDiProva(),
                app = componentiDiProva(),
            ).also(sessioni::add)
            sessione.crea(cartella.toString(), "Prova").atteso()
            val scope = checkNotNull(sessione.collaboratoriCorrenti()).scope

            // Un coroutine per-progetto guasto e uno FRATELLO, sullo STESSO scope figlioDi: solo il primo fallisce.
            scope.launch { error("guasto iniettato") }
            val jobFratello: Job = scope.launch { fratelloAttendeAncora.await() }

            attendiFinche(messaggio = "l'errore iniettato e' stato segnalato una volta") { catturati.isNotEmpty() }
            assertEquals(1, catturati.size, "segnalato esattamente una volta, mai per ogni retry o duplicato")
            assertTrue(scope.isActive, "AC-C55: lo scope del progetto sopravvive all'errore di un suo figlio")
            assertTrue(jobFratello.isActive, "AC-C55: il job fratello non e' cancellato dal guasto del suo vicino")

            fratelloAttendeAncora.complete(Unit)
            attendiFinche(messaggio = "il job fratello termina pulito") { jobFratello.isCompleted }
            assertFalse(jobFratello.isCancelled, "il job fratello completa normalmente, mai cancellato")

            sessione.chiudi()
        } finally {
            logger.removeHandler(spia)
        }
    }

    @Volatile private var ultimoPercorso: String? = null

    /** True while another channel of this JVM holds `.lock` (tryLock then throws OverlappingFileLockException). */
    private fun lockTenuto(cartellaProgetto: Path): Boolean =
        FileChannel.open(cartellaProgetto.resolve(".lock"), StandardOpenOption.WRITE).use { canale ->
            try {
                canale.tryLock()?.release()
                false
            } catch (ignored: OverlappingFileLockException) {
                true
            }
        }
}
