package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.dominio.ErroreProgetto
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")

private fun rigaVista(id: RegistrazioneId, titolo: String = "Seduta") =
    RegistrazioneDelProgettoVista(id, titolo, LocalDate.of(2026, 3, 12), 60_000)

/**
 * AC-363: renaming a Registrazione from S2's inline titolo field — [RegistrazioniPresenter.rinomina]
 * (split from `RegistrazioniPresenterTest`, which only builds the presenter with a failing `rinomina`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniRinominaTest {
    private fun presentatore(
        scope: TestScope,
        registrazioni: () -> List<RegistrazioneDelProgettoVista>,
        rinomina: (RinominaRegistrazione) -> Esito<Unit>,
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = registrazioni,
            aggiungiRegistrazione = { error("aggiungi non atteso in questo test") },
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = rinomina,
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `AC-363 rinomina riuscita invia il comando e ricarica la lista con il nuovo titolo`() = runTest {
        var titoloCorrente = "Seduta"
        val comandi = mutableListOf<RinominaRegistrazione>()
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, titolo = titoloCorrente)) },
            rinomina = { c ->
                comandi += c
                titoloCorrente = c.nuovoTitolo
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(REG_1, "Consiglio di marzo")
        advanceUntilIdle()

        assertEquals(listOf(RinominaRegistrazione(REG_1, "Consiglio di marzo")), comandi)
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals("Consiglio di marzo", riga.titolo)
        assertNull(riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
    }

    @Test
    fun `AC-363 un errore di rinomina e mostrato inline sulla riga e il vecchio titolo resta`() = runTest {
        var letture = 0
        val presenter = presentatore(
            this,
            registrazioni = {
                letture++
                listOf(rigaVista(REG_1, titolo = "Seduta"))
            },
            rinomina = { Esito.Errore(ErroreProgetto.TitoloGiaUsato("Intervista")) },
        )
        advanceUntilIdle()
        val lettureIniziali = letture

        presenter.azioni.rinomina(REG_1, "Intervista")
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(messaggioPer(ErroreProgetto.TitoloGiaUsato("Intervista")), riga.erroreRiga)
        assertEquals("Seduta", riga.titolo)
        assertEquals(false, riga.operazioneInCorso)
        assertEquals(lettureIniziali, letture, "dopo un errore la lista non viene ricaricata")
    }

    @Test
    fun `AC-363 un titolo vuoto rifiutato mostra il messaggio TitoloVuoto sulla riga`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, titolo = "Seduta")) },
            rinomina = { Esito.Errore(ErroreProgetto.TitoloVuoto) },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(REG_1, "  ")
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(messaggioPer(ErroreProgetto.TitoloVuoto), riga.erroreRiga)
        assertEquals("Seduta", riga.titolo)
    }

    @Test
    fun `AC-363 M3 Invio seguito dalla perdita del focus rinomina una sola volta`() = runTest {
        var chiamate = 0
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            rinomina = {
                chiamate++
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(REG_1, "Nuovo titolo")
        presenter.azioni.rinomina(REG_1, "Nuovo titolo")
        advanceUntilIdle()

        assertEquals(1, chiamate)
    }

    @Test
    fun `AC-363 un eccezione durante la rinomina mostra il messaggio generico sulla riga`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, titolo = "Seduta")) },
            rinomina = { error("database non disponibile") },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(REG_1, "Nuovo titolo")
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(MESSAGGIO_ERRORE_GENERICO, riga.erroreRiga)
        assertEquals("Seduta", riga.titolo)
    }
}
