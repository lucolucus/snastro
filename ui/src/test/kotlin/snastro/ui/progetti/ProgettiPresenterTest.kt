package snastro.ui.progetti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoAperto
import snastro.ui.SessioneProgetto
import snastro.ui.SessioneProgettoFinta
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val ORA: Instant = Instant.parse("2026-09-23T10:15:30Z")

/**
 * A [SessioneProgetto] whose `crea`/`apri` return a fixed [Esito] chosen per test (RC-9/CR-17: a
 * hand-written fake, not a MockK stub) — the block-spec's own scenario, e.g. a `crea` that fails with
 * `CartellaNonValida` (AC-195) or an `apri` that fails with `ProgettoGiaAperto` (AC-196).
 */
private class SessioneProgettoFissa(
    private val risultatoCrea: Esito<ProgettoAperto>? = null,
    private val risultatoApri: Esito<ProgettoAperto>? = null,
) : SessioneProgetto {
    override val corrente: StateFlow<ProgettoAperto?> = MutableStateFlow(null)

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> =
        risultatoCrea ?: error("crea non atteso in questo test")

    override fun apri(percorso: String): Esito<ProgettoAperto> =
        risultatoApri ?: error("apri non atteso in questo test")

    override fun chiudi() = Unit
}

/** Counts `crea` calls (M3): wraps [SessioneProgettoFinta] by delegation rather than a MockK spy. */
private class SessioneProgettoContaChiamate(private val delegato: SessioneProgetto = SessioneProgettoFinta()) :
    SessioneProgetto by delegato {
    var chiamateCrea = 0
        private set

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        chiamateCrea++
        return delegato.crea(cartellaGenitore, nome)
    }
}

/**
 * fix-batch-12 #5: the FIRST `crea` throws a `CancellationException` that is NOT a real cancellation
 * of this presenter's own job (nothing here ever calls `.cancel()`) — the same "spurious cancellation
 * from the port" family LettorePresenter's own `ensureActive()` fix (#7) handles. Every call after
 * the first delegates normally, so a test can prove a LATER `crea` still reaches [SessioneProgetto].
 */
private class SessioneProgettoAnnullaLaPrimaVolta(private val delegato: SessioneProgetto = SessioneProgettoFinta()) :
    SessioneProgetto by delegato {
    var chiamate = 0
        private set
    private var primaVolta = true

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        chiamate++
        if (primaVolta) {
            primaVolta = false
            throw CancellationException("annullato")
        }
        return delegato.crea(cartellaGenitore, nome)
    }
}

private class RegistroProgettiCheLanciaSempre : RegistroProgetti {
    override fun elenco(): List<VoceRegistro> = error("registro rotto")
    override fun registra(v: VoceRegistro): Unit = error("registro rotto")
    override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant): Unit =
        error("registro rotto")
    override fun rimuovi(percorso: String): Unit = error("registro rotto")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgettiPresenterTest {
    // A scope of its own (sharing the test's scheduler, but NOT a child of `runTest`'s own job) —
    // same rationale as ShellPresenterTest: the coroutine started by `init` completes on its own
    // (unlike the shell's never-ending collector), but keeping the pattern identical costs nothing.
    private fun presentatore(
        scope: TestScope,
        registro: RegistroProgetti = RegistroProgettiFinta(),
        sessione: SessioneProgetto = SessioneProgettoFinta(),
    ): ProgettiPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return ProgettiPresenter(CoroutineScope(dispatcher), dispatcher, ElencoProgetti(registro), sessione)
    }

    @Test
    fun `AC-193 prima del caricamento dell elenco lo stato e Caricamento`() = runTest {
        val presenter = presentatore(this)
        assertEquals(ProgettiUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `AC-192 un registro vuoto produce una lista vuota`() = runTest {
        val presenter = presentatore(this)
        advanceUntilIdle()
        assertEquals(ProgettiUiStato.Dati(progetti = emptyList()), presenter.stato.value)
    }

    @Test
    fun `AC-198 la lista espone progettoId nome percorso numRegistrazioni e ultimaAttivita di ogni progetto`() =
        runTest {
            val registro = RegistroProgettiFinta().apply {
                registra(VoceRegistro(ProgettoId("id-1"), "Consiglio comunale", "/p/Consiglio.snastro", 3, ORA))
            }
            val presenter = presentatore(this, registro = registro)
            advanceUntilIdle()

            val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
            assertEquals(
                listOf(ProgettoVista(ProgettoId("id-1"), "Consiglio comunale", "/p/Consiglio.snastro", 3, ORA)),
                stato.progetti,
            )
        }

    @Test
    fun `AC-194 crea con nome vuoto mostra il messaggio di errore mappato inline`() = runTest {
        val presenter = presentatore(this)
        advanceUntilIdle()

        presenter.crea("/tmp", "")
        advanceUntilIdle()

        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.NomeProgettoVuoto), stato.erroreCrea)
        assertNull(stato.erroreApri)
        assertEquals(false, stato.inCorso)
    }

    @Test
    fun `AC-195 una creazione fallita con un errore diverso da nome vuoto mostra un errore inline`() = runTest {
        val sessione = SessioneProgettoFissa(risultatoCrea = Esito.Errore(ErroreSessione.CartellaNonValida))
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()

        presenter.crea("/percorso/non/valido", "Riunione")
        advanceUntilIdle()

        // L464e/f: `assertNull(sessione.corrente.value)` era tautologico — `SessioneProgettoFissa.corrente`
        // e' un MutableStateFlow(null) che nessun codice qui aggiorna mai, quindi restava null a
        // prescindere dal comportamento del presenter. Le asserzioni utili sono sullo STATO del presenter.
        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), stato.erroreCrea)
        assertEquals(false, stato.inCorso)
        assertNull(stato.erroreApri)
    }

    @Test
    fun `AC-196 aprire un progetto gia aperto da un altra istanza mostra il messaggio dedicato`() = runTest {
        val sessione = SessioneProgettoFissa(risultatoApri = Esito.Errore(ErroreSessione.ProgettoGiaAperto))
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()

        presenter.apri("/progetti/Riunione.snastro")
        advanceUntilIdle()

        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.ProgettoGiaAperto), stato.erroreApri)
        assertNull(stato.erroreCrea)
    }

    @Test
    fun `AC-197 apri progetto con una cartella non valida mostra un errore inline`() = runTest {
        val sessione = SessioneProgettoFissa(risultatoApri = Esito.Errore(ErroreSessione.CartellaNonValida))
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()

        presenter.apri("/percorso/inesistente")
        advanceUntilIdle()

        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), stato.erroreApri)
    }

    @Test
    fun `H1 chiudiErroreCrea e chiudiErroreApri rimuovono solo il rispettivo messaggio`() = runTest {
        val sessione = SessioneProgettoFissa(
            risultatoCrea = Esito.Errore(ErroreSessione.CartellaNonValida),
            risultatoApri = Esito.Errore(ErroreSessione.ProgettoGiaAperto),
        )
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()
        presenter.apri("/progetti/Riunione.snastro")
        advanceUntilIdle()
        assertIs<ProgettiUiStato.Dati>(presenter.stato.value).let {
            assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), it.erroreCrea)
            assertEquals(messaggioPer(ErroreSessione.ProgettoGiaAperto), it.erroreApri)
        }

        presenter.chiudiErroreCrea()
        val dopoChiudiCrea = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertNull(dopoChiudiCrea.erroreCrea)
        assertEquals(messaggioPer(ErroreSessione.ProgettoGiaAperto), dopoChiudiCrea.erroreApri)

        presenter.chiudiErroreApri()
        val dopoChiudiApri = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertNull(dopoChiudiApri.erroreApri)
    }

    @Test
    fun `un nuovo crea pulisce l errore precedente anche prima del risultato`() = runTest {
        val presenter = presentatore(this)
        advanceUntilIdle()
        presenter.crea("/tmp", "")
        advanceUntilIdle()
        assertNotNull(assertIs<ProgettiUiStato.Dati>(presenter.stato.value).erroreCrea)

        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()

        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertNull(stato.erroreCrea)
        assertEquals(false, stato.inCorso)
    }

    @Test
    fun `M3 due richieste crea ravvicinate eseguono sessione crea una sola volta`() = runTest {
        val sessione = SessioneProgettoContaChiamate()
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()

        presenter.crea("/tmp", "Uno")
        presenter.crea("/tmp", "Due")
        advanceUntilIdle()

        assertEquals(1, sessione.chiamateCrea)
    }

    @Test
    fun `fix-batch-12 5 una CancellationException di crea non lascia inCorso bloccato`() = runTest {
        val sessione = SessioneProgettoAnnullaLaPrimaVolta()
        val presenter = presentatore(this, sessione = sessione)
        advanceUntilIdle()

        presenter.crea("/tmp", "Uno")
        advanceUntilIdle()
        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(false, stato.inCorso, "inCorso bloccato a true impedirebbe ogni crea/apri successivo (M3)")

        // il guardiano M3 non e' piu' bloccato: un secondo crea arriva davvero a sessione.crea.
        presenter.crea("/tmp", "Due")
        advanceUntilIdle()
        assertEquals(2, sessione.chiamate, "il secondo crea deve raggiungere sessione.crea, non essere ignorato da M3")
    }

    @Test
    fun `fix-batch-12 5 un fallimento del caricamento iniziale mostra un errore generico con le azioni utilizzabili`() =
        runTest {
            val presenter = presentatore(this, registro = RegistroProgettiCheLanciaSempre())
            advanceUntilIdle()

            // L530d: il fallimento del caricamento INIZIALE atterra su `erroreElenco`, non su
            // `erroreCrea` (quel campo e' un messaggio per-form di un `crea` fallito, un'altra cosa).
            val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
            assertEquals(emptyList(), stato.progetti)
            assertEquals(MESSAGGIO_ERRORE_GENERICO, stato.erroreElenco)
            assertNull(stato.erroreCrea)
            assertEquals(false, stato.inCorso)
        }

    @Test
    fun `L530d riprova rimostra Caricamento e poi il nuovo esito`() = runTest {
        var lanciaSempre = true
        val registro = object : RegistroProgetti by RegistroProgettiFinta() {
            override fun elenco(): List<VoceRegistro> =
                if (lanciaSempre) error("registro rotto") else emptyList()
        }
        val presenter = presentatore(this, registro = registro)
        advanceUntilIdle()
        assertEquals(MESSAGGIO_ERRORE_GENERICO, assertIs<ProgettiUiStato.Dati>(presenter.stato.value).erroreElenco)

        lanciaSempre = false
        presenter.azioni.riprova()
        assertEquals(ProgettiUiStato.Caricamento, presenter.stato.value, "riprova deve rimostrare Caricamento subito")
        advanceUntilIdle()

        val stato = assertIs<ProgettiUiStato.Dati>(presenter.stato.value)
        assertEquals(emptyList(), stato.progetti)
        assertNull(stato.erroreElenco)
    }
}
