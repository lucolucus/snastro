package snastro.avvio.accettazione

import kotlinx.coroutines.ExperimentalCoroutinesApi
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.avvio.progetto.conSessioneDiProva
import snastro.kernel.IncontroId
import snastro.kernel.atteso
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.IncontroRepositorySql
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.dominio.Incontro
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.ui.ProgettoAperto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R0 · elimina con lock e coda (EX-6, EX-11): the list's presenter over the REAL [SessioneProgettoImpl] and real
 * project folders under `~/Documents/snastro` — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. EX-6's other window is a second real
 * session in this JVM holding the project's real `.lock` (its own registry, so its registration cannot race the
 * list). EX-11's pending summary is written into the closed project's real database through the production SQL
 * repositories. The presenter runs on [BancoS1]'s dispatcher, shared with `runTest` and advanced explicitly, its
 * scope cancelled in teardown; the sessions, built by [conSessione] and [conSessioneDiProva], are closed and their
 * scopes cancelled when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaConLockECodaTest : BancoS1() {
    @Test
    fun `EX-6 mangu 2 aperto in un'altra finestra non si elimina dalla lista, lo si dice e niente e cancellato`() =
        conSessione(registro, dispatcher) { sessione ->
            val mangu = creaChiuso(sessione, "mangu 2")
            val presenter = lista(sessione)
            conSessioneDiProva(RegistroProgettiFinta()) { altraFinestra ->
                altraFinestra.apri(mangu.percorso).atteso()
                val primaDiEliminare = contenuto(Path.of(mangu.percorso))

                elimina(presenter, "mangu 2")

                assertEquals("Questo progetto è aperto in un'altra finestra.", dati(presenter).conferma?.errore)
                assertEquals(primaDiEliminare, contenuto(Path.of(mangu.percorso)), "niente cancellato")
                assertEquals(listOf("mangu 2"), registro.elenco().map { it.nome })
            }
        }

    @Test
    fun `EX-11 mangu 2 chiuso con un riassunto in attesa nel suo DB si elimina e sparisce dalla lista`() =
        conSessione(registro, dispatcher) { sessione ->
            creaChiuso(sessione, "skatefest")
            val mangu = creaChiuso(sessione, "mangu 2")
            riassuntoInAttesa(mangu)
            val presenter = lista(sessione)

            elimina(presenter, "mangu 2")

            assertFalse(Files.exists(Path.of(mangu.percorso)), "mangu 2.snastro cancellata")
            assertEquals(listOf("skatefest"), nomi(presenter))
            assertNull(dati(presenter).conferma)
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
}
