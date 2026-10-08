package snastro.avvio.accettazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.atteso
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.IncontroRepositorySql
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.dominio.Incontro
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.conScopeDiProva
import snastro.ui.ProgettoAperto
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiUiStato
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 · elimina con lock e coda (EX-6, EX-11): the list's presenter over the REAL [SessioneProgettoImpl] and real
 * project folders under `~/Documents/snastro` — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. EX-6's other window is a second real
 * session in this JVM holding the project's real `.lock` (its own registry, so its registration cannot race the
 * list). EX-11's pending summary is written into the closed project's real database through the production SQL
 * repositories. The presenter runs on a [StandardTestDispatcher] shared with `runTest` and advanced explicitly, its
 * scope cancelled in teardown; the sessions get their scope from [conScopeDiProva], cancelled when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaConLockECodaTest {
    @TempDir
    lateinit var casa: Path

    private val documenti: Path by lazy { casa.resolve("Documents/snastro") }

    private val registro = RegistroProgettiFinta()
    private val orologio = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val scopePresenter = CoroutineScope(SupervisorJob() + dispatcher)

    @AfterEach
    fun chiudi() = scopePresenter.cancel()

    @Test
    fun `EX-6 mangu 2 aperto in un'altra finestra non si elimina dalla lista, lo si dice e niente e cancellato`() =
        conScopeDiProva { scope ->
            val sessione = sessione(registro, scope)
            val altraFinestra = sessione(RegistroProgettiFinta(), scope)
            try {
                runTest(dispatcher) {
                    val mangu = creaChiuso(sessione, "mangu 2")
                    val presenter = lista(sessione)
                    altraFinestra.apri(mangu.percorso).atteso()
                    val primaDiEliminare = contenuto(Path.of(mangu.percorso))

                    elimina(presenter, "mangu 2")

                    assertEquals("Questo progetto è aperto in un'altra finestra.", dati(presenter).conferma?.errore)
                    assertEquals(primaDiEliminare, contenuto(Path.of(mangu.percorso)), "niente cancellato")
                    assertEquals(listOf("mangu 2"), registro.elenco().map { it.nome })
                }
            } finally {
                altraFinestra.chiudi()
                sessione.chiudi()
            }
        }

    @Test
    fun `EX-11 mangu 2 chiuso con un riassunto in attesa nel suo DB si elimina e sparisce dalla lista`() =
        conScopeDiProva { scope ->
            val sessione = sessione(registro, scope)
            try {
                runTest(dispatcher) {
                    creaChiuso(sessione, "skatefest")
                    val mangu = creaChiuso(sessione, "mangu 2")
                    riassuntoInAttesa(mangu)
                    val presenter = lista(sessione)

                    elimina(presenter, "mangu 2")

                    assertFalse(Files.exists(Path.of(mangu.percorso)), "mangu 2.snastro cancellata")
                    assertEquals(listOf("skatefest"), dati(presenter).progetti.map(ProgettoVista::nome))
                    assertNull(dati(presenter).conferma)
                }
            } finally {
                sessione.chiudi()
            }
        }

    private fun sessione(registro: RegistroProgettiFinta, scope: CoroutineScope) =
        SessioneProgettoImpl(registro, GeneratoreIdFinto(), orologio, scope, componentiDiProva())

    /** [nome] created, closed and registered in [registro]. */
    private fun creaChiuso(sessione: SessioneProgettoImpl, nome: String): ProgettoAperto {
        val creato = sessione.crea(documenti.toString(), nome).atteso()
        sessione.chiudi()
        attendiFinche(timeout = 10.seconds, messaggio = "$nome registrato") {
            registro.elenco().any { it.percorso == creato.percorso }
        }
        return creato
    }

    /** The meeting of 2026-10-07 with a summary still `in_attesa`: the project was closed before it started. */
    private fun riassuntoInAttesa(progetto: ProgettoAperto) {
        val db = apriDatabaseProgetto(Path.of(progetto.percorso).toFile())
        try {
            val incontro = IncontroId("incontro-2026-10-07")
            val unitaDiLavoro = UnitaDiLavoroSql(db.database)
            val riassunti = RiassuntoRepositorySql(db.database, unitaDiLavoro)
            unitaDiLavoro.inTransazione {
                IncontroRepositorySql(db.database).salva(Incontro.nuovo(incontro, progetto.progettoId))
                riassunti.salva(unRiassunto("riassunto-in-attesa", incontro))
            }.atteso()
            assertTrue(riassunti.inAttesa().isNotEmpty(), "il riassunto e rimasto in attesa")
        } finally {
            db.chiudi()
        }
    }

    private fun TestScope.lista(sessione: SessioneProgettoImpl): ProgettiPresenter =
        ProgettiPresenter(scopePresenter, dispatcher, ElencoProgetti(registro), sessione, casa.toString())
            .also { advanceUntilIdle() }

    private fun TestScope.elimina(presenter: ProgettiPresenter, nome: String) {
        presenter.azioni.chiediElimina(dati(presenter).progetti.single { it.nome == nome })
        presenter.azioni.confermaElimina(nome)
        advanceUntilIdle()
    }

    /** What is in [cartella], but the database's WAL files: the other window's open database makes and drops them. */
    private fun contenuto(cartella: Path): List<String> = Files.list(cartella).use { percorsi ->
        percorsi.map { it.fileName.toString() }.filter { !it.startsWith("progetto.db-") }.sorted().toList()
    }

    private fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)
}
