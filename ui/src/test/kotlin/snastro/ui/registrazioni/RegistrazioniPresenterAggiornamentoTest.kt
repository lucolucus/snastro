package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)
private val ORA_FISSA: Instant = Instant.parse("2026-09-23T10:00:00Z")

private fun rigaVista(id: RegistrazioneId, titolo: String = "Seduta") =
    RegistrazioneDelProgettoVista(id, titolo, DATA_1, durataMs = 60_000)

/**
 * [RegistrazioniPresenter] refresh/error-lifecycle tests split out of [RegistrazioniPresenterTest] (CR-9/
 * detekt `LargeClass`): L485a (refresh vs import error are separate lifecycles) and L485e (`riprova`
 * shows `Caricamento` right away).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniPresenterAggiornamentoTest {
    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator these tests vary
    private fun presentatore(
        scope: TestScope,
        registrazioni: () -> List<RegistrazioneDelProgettoVista> = { emptyList() },
        aggiungi: (AggiungiRegistrazione) -> Esito<Unit> = { error("aggiungi non atteso in questo test") },
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
        stati: ((List<RegistrazioneId>) -> List<StatoRegistrazioneVista>)? = null,
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = registrazioni,
            aggiungiRegistrazione = aggiungi,
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = { error("rinomina non atteso in questo test") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = aggiornamenti,
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = stati,
        )
    }

    @Test
    fun `L485e riprova mostra subito Caricamento invece di lasciare lo schermo di errore fermo`() = runTest {
        val presenter = presentatore(this, registrazioni = { error("guasto") })
        advanceUntilIdle()
        assertIs<RegistrazioniUiStato.Errore>(presenter.stato.value)

        presenter.azioni.riprova()

        // synchronously, before the reload even resolves — never left showing the old Errore screen
        // with no feedback that a retry is under way.
        assertEquals(RegistrazioniUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `L485a un refresh riuscito cancella l errore di un refresh precedente`() = runTest {
        var fallisce = false
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            registrazioni = { if (fallisce) error("guasto di rete") else listOf(rigaVista(REG_1)) },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()

        fallisce = true
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()
        assertNotNull(assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).erroreAggiornamento)

        fallisce = false
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertNull(assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).erroreAggiornamento)
    }

    @Test
    fun `L485a un errore di importazione sopravvive a un refresh di sfondo riuscito`() = runTest {
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            aggiungi = { Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(it.percorsoSorgente)) },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        presenter.azioni.importa(listOf("/sorgenti/x.m4a"))
        advanceUntilIdle()
        assertNotNull(assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).errore)

        aggiornamenti.emetti(Cambiamento(REG_1)) // an UNRELATED background refresh, succeeds
        advanceUntilIdle()

        // M1: an unrelated (successful) refresh never wipes the import's own error.
        assertNotNull(assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).errore)
    }
}
