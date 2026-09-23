package snastro.avvio

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
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
import snastro.ui.ErroreSessione
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    @TempDir
    lateinit var cartella: Path

    private val orologio: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)

    private fun scopeDiProva(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Test
    fun `H2 chiudi cancella lo scope di sessione e chiude il lettore reale`() {
        val riproduttoreFinto = mockk<RiproduttoreWav>(relaxed = true)
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
            seams = SessioneProgettoSeams(riproduttoreFabbrica = { riproduttoreFinto }),
        )
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
            seams = SessioneProgettoSeams(
                riproduttoreFabbrica = { mockk<RiproduttoreWav>(relaxed = true).also { riproduttori += it } },
            ),
        )

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
            seams = SessioneProgettoSeams(
                costruisciRegistrazioni = { db -> RegistrazioneRepositoryCheLancia(RegistrazioneRepositorySql(db)) },
            ),
        )
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()

        sessione.chiudi() // non deve lanciare, nonostante delProgetto rotto (stessa regola AC-347)

        assertNull(sessione.corrente.value)
        val riaperta = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = orologio,
            scopeGenitore = scopeDiProva(),
        )
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
            seams = SessioneProgettoSeams(chiudiDatabase = { throw IllegalStateException("checkpoint fallito") }),
        )
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
        )
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
            seams = SessioneProgettoSeams(chiudiDatabase = { throw IllegalStateException("checkpoint fallito") }),
        )

        val errore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()

        assertEquals(ErroreSessione.CartellaNonValida, errore)
        // il lock non e' rimasto trattenuto: una seconda apri fallisce di nuovo per lo stesso motivo,
        // mai per ProgettoGiaAperto.
        val secondoErrore = sessione.apri(cartellaProgetto.toString()).erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.CartellaNonValida, secondoErrore)
    }
}
