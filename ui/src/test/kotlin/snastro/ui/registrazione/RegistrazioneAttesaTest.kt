package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import snastro.parlanti.applicazione.letture.PropostaVista
import java.time.Clock
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val TENTATIVI = 400
private const val PAUSA_MS = 5L

private fun pool(nome: String, thread: Int): ExecutorCoroutineDispatcher =
    Executors.newFixedThreadPool(thread) { r -> Thread(r, nome).apply { isDaemon = true } }.asCoroutineDispatcher()

/**
 * ADR 0017 §3 on REAL threads: a Proposta held on the (blocking) native Mutex (AC-416) and the
 * "never on the UI thread" rule (AC-417). The blocking reads run on a real background pool, the
 * presenter's own scope on virtual time ([TestScope]) or on a real "UI" thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneAttesaTest {
    private fun TestScope.attendi(condizione: () -> Boolean) {
        repeat(TENTATIVI) {
            runCurrent()
            if (condizione()) return
            Thread.sleep(PAUSA_MS)
        }
        fail("condizione non raggiunta")
    }

    private fun attendiReale(condizione: () -> Boolean) {
        repeat(TENTATIVI) {
            if (condizione()) return
            Thread.sleep(PAUSA_MS)
        }
        fail("condizione non raggiunta")
    }

    private fun RegistrazionePresenter.carta(voce: snastro.kernel.VoceId): CartaVoce? =
        (stato.value as? RegistrazioneUiStato.Dati)?.pannello?.carte?.single { it.voceId == voce }

    @Test
    fun `AC-416 Proposta non arrivata oltre la soglia mostra l attesa senza Annulla e altri nuovo salta abilitati`() =
        runTest {
            val io = pool("io-test", thread = 2)
            val mutex = CountDownLatch(1)
            val a = AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))
            a.proposta = {
                mutex.await()
                PropostaVista(it.voceId, listOf(unCandidato()))
            }
            val presenter = a.presenter(CoroutineScope(StandardTestDispatcher(testScheduler)), io)
            attendi { presenter.carta(V1)?.contenuto is ContenutoCarta.DaIdentificare }
            val carta = { assertIs<ContenutoCarta.DaIdentificare>(presenter.carta(V1)?.contenuto) }
            assertEquals(StatoProposta.Caricamento, carta().proposta)

            advanceTimeBy(RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS + 1)
            runCurrent()
            assertEquals(StatoProposta.InAttesa, carta().proposta)
            assertNull(presenter.carta(V1)?.inCorso) // no 'Annulla' for a Proposta: it is a read
            assertTrue(presenter.carta(V1)?.azioniAbilitate == true) // 'altri ▾' / 'nuovo…' / 'salta'
            assertFalse(presenter.carta(V1)?.confermaAbilitata == true)

            presenter.azioni.salta(V1)
            attendi { a.comandi.eseguiti.isNotEmpty() }
            attendi { presenter.carta(V1)?.contenuto is ContenutoCarta.Attribuita }
            mutex.countDown()
            io.close()
        }

    @Test
    fun `AC-417 comandi e Proposte non girano mai sul thread UI ma sul dispatcher iniettato`() = runBlocking {
        val ui = pool("ui-test", thread = 1)
        val io = pool("io-test", thread = 2)
        val threadProposta: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
        val a = AmbienteVoci(CoroutineScope(Dispatchers.Default), Clock.systemUTC())
        val proposta = a.proposta
        a.proposta = { r ->
            threadProposta += Thread.currentThread()
            proposta(r)
        }
        val schermata = CoroutineScope(ui)
        val presenter = a.presenter(schermata, io)
        attendiReale {
            (presenter.carta(V1)?.contenuto as? ContenutoCarta.DaIdentificare)?.proposta is StatoProposta.Pronta
        }
        withContext(ui) { presenter.azioni.conferma(V1) }
        attendiReale { a.comandi.eseguiti.isNotEmpty() }

        val main = Thread.currentThread()
        val usati = a.comandi.thread + threadProposta
        assertTrue(a.comandi.thread.isNotEmpty() && threadProposta.isNotEmpty())
        usati.forEach {
            assertEquals("io-test", it.name)
            assertNotEquals(main, it)
        }
        schermata.cancel()
        ui.close()
        io.close()
    }
}
