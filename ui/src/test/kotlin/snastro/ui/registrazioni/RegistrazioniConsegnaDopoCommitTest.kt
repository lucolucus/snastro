package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG = RegistrazioneId("id-1")

/**
 * L270 (D-0062): every S2 command that commits shows its committed outcome when only an after-commit subscriber
 * failed. L269 (D-0065): an Error escaping after a committed write propagates, but never leaves S2 in progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniConsegnaDopoCommitTest {
    private var letture = 0
    private val inviati = mutableListOf<Any>()
    private val sfuggite = mutableListOf<Throwable>()
    private var guasto: () -> Throwable = { ConsegnaDopoCommitFallita(IllegalStateException("abbonato fallito")) }

    /** Records [comando] as committed, then fails like the after-commit delivery does. */
    private fun confermatoPoi(comando: Any): Esito<Unit> {
        inviati += comando
        throw guasto()
    }

    private fun presentatore(scope: TestScope): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            // SupervisorJob: each L269 case below runs even after an earlier Error reached the handler.
            scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> sfuggite += e }),
            io = dispatcher,
            progettoId = ProgettoId("progetto-1"),
            registrazioni = {
                letture++
                listOf(RegistrazioneDelProgettoVista(REG, "Seduta", LocalDate.of(2026, 3, 12), 60_000))
            },
            incontri = { emptyList() },
            aggiungiRegistrazione = ::confermatoPoi,
            modificaDataRegistrazione = ::confermatoPoi,
            rinominaRegistrazione = ::confermatoPoi,
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC),
            statiElaborazione = { ids -> ids.map(::nonAvviata) },
            avviaElaborazione = ::confermatoPoi,
            apriRegistrazione = { error("non atteso") },
            identificazioniIncontri = { emptyMap() },
            ritrascrivi = ::confermatoPoi,
            annullaElaborazione = ::confermatoPoi,
            eliminaRegistrazione = ::confermatoPoi,
            posizioniNellaCoda = { PosizioniCoda.VUOTA },
            avviaElaborazioniDellIncontro = ::confermatoPoi,
            modificaOraDiInizioRegistrazione = { id, ora -> confermatoPoi(id to ora) },
            numeroPersonePrecompilato = { null },
        )
    }

    private fun nonAvviata(id: RegistrazioneId) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.NON_AVVIATA,
        fase = null,
        avviataAlle = null,
        motivoFallimento = null,
        numVoci = null,
        numeroPersone = null,
        trascrittoDisponibile = false,
        elaborazioneId = ElaborazioneId("elaborazione-1"),
    )

    private fun RegistrazioniPresenter.dati() = assertIs<RegistrazioniUiStato.Dati>(stato.value)

    private fun RegistrazioniPresenter.riga() = dati().righe.single()

    @Test
    fun `L270 ogni comando di riga confermato con un abbonato dopo-commit fallito ricarica senza errore`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()
        val comandi: List<Pair<String, () -> Unit>> = listOf(
            "modificaData" to { p.azioni.modificaData(REG, LocalDate.of(2026, 4, 1)) },
            "rinomina" to { p.azioni.rinomina(REG, "Nuovo titolo") },
            "modificaOraDiInizio" to { p.azioni.modificaOraDiInizio(REG, LocalTime.of(9, 30)) },
            "avviaElaborazione" to { p.azioni.avviaElaborazione(REG) },
            "confermaRitrascrivi" to {
                p.azioni.ritrascrivi(REG)
                p.azioni.confermaRitrascrivi(REG)
            },
            "annullaElaborazione" to { p.azioni.annullaElaborazione(REG) },
            "confermaElimina" to {
                p.azioni.elimina(REG)
                p.azioni.confermaElimina(REG)
            },
        )
        for ((nome, comando) in comandi) {
            val prima = letture
            comando()
            advanceUntilIdle()
            assertNull(p.riga().erroreRiga, "$nome: un comando confermato non e mai un errore")
            assertEquals(false, p.riga().operazioneInCorso, nome)
            assertTrue(letture > prima, "$nome: la lista e riletta come su Ok")
        }
        assertEquals(comandi.size, inviati.size)
        assertTrue(sfuggite.isEmpty())
    }

    @Test
    fun `L270 Trascrivi N parti confermato con un abbonato dopo-commit fallito non mostra errore`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()
        val prima = letture

        p.azioni.avviaElaborazioniIncontro(IncontroId(REG.valore))
        advanceUntilIdle()

        val incontro = p.dati().incontri.single()
        assertNull(incontro.errore)
        assertEquals(false, incontro.operazioneInCorso)
        assertTrue(letture > prima)
    }

    @Test
    fun `L269 un Error dopo il commit si propaga ma non lascia la riga ne l import in corso`() = runTest {
        guasto = { AssertionError("abbonato dopo-commit: errore di programmazione") }
        val p = presentatore(this)
        advanceUntilIdle()

        p.azioni.rinomina(REG, "Nuovo titolo")
        advanceUntilIdle()
        assertEquals(false, p.riga().operazioneInCorso, "la riga non resta bloccata")

        p.azioni.importa(listOf("/sorgenti/a.m4a"))
        advanceUntilIdle()
        assertEquals(false, p.dati().importoInCorso, "l import non resta in corso")

        p.azioni.importa(listOf("/sorgenti/a.m4a", "/sorgenti/b.m4a"))
        p.azioni.confermaImporta()
        advanceUntilIdle()
        assertEquals(false, p.dati().dialogoImporta?.invioInCorso, "il dialogo non resta in invio")

        p.azioni.annullaImporta()
        p.azioni.aggiungiParti(IncontroId(REG.valore), "Seduta", listOf("/sorgenti/c.m4a"))
        advanceUntilIdle()
        assertEquals(false, p.dati().importoInCorso)

        assertEquals(4, sfuggite.size, "ogni Error si propaga al gestore dello scope (D-0065)")
        assertTrue(sfuggite.all { it is AssertionError })
        assertTrue(inviati.filterIsInstance<AggiungiRegistrazione>().size == 3)
    }
}
