package snastro.ui.lettore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val REGISTRAZIONE_ID = RegistrazioneId("id-1")

/**
 * [LettoreAudio] whose `riproduciDa` throws instead of setting `stato` — a hand-written fake (RC-9/
 * CR-17), not a MockK stub. `falliscePer` lets a test make only ONE id fail, so it can also prove the
 * presenter/its `stato` collector survive the fault (HIGH-2).
 */
private class LettoreAudioCheEsplode(
    private val eccezione: () -> Throwable,
    private val falliscePer: RegistrazioneId? = null,
) : LettoreAudio {
    private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
    override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

    override fun disponibile(id: RegistrazioneId): Boolean = true

    override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
        if (falliscePer == null || id == falliscePer) throw eccezione()
        _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
    }

    override fun riproduciEstratto(e: EstrattoRef): Nothing = throw eccezione()

    override fun pausa() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class LettorePresenterTest {
    private fun presentatore(scope: TestScope, lettore: LettoreAudio = LettoreAudioFinta()): LettorePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return LettorePresenter(CoroutineScope(dispatcher), dispatcher, lettore)
    }

    @Test
    fun `stato iniziale e Inattivo`() = runTest {
        val presenter = presentatore(this)
        assertEquals(LettoreUiStato.Inattivo, presenter.stato.value)
    }

    @Test
    fun `AC-191 riproduci mostra Caricamento finche il lettore non e pronto`() = runTest {
        val presenter = presentatore(this)

        presenter.riproduci(REGISTRAZIONE_ID, 0)
        assertEquals(LettoreUiStato.Caricamento, presenter.stato.value)

        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
    }

    @Test
    fun `AC-187 play e pausa funzionano e la posizione avanza durante la riproduzione`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, fake)

        presenter.riproduci(REGISTRAZIONE_ID, 0)
        advanceUntilIdle()
        var stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(0, stato.posizioneMs)
        assertEquals(true, stato.inRiproduzione)

        // the underlying player reports a later tick (AC-187: position advances during playback)
        fake.emetti(StatoLettore(REGISTRAZIONE_ID, 500, inRiproduzione = true))
        advanceUntilIdle()
        stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(500, stato.posizioneMs)
        assertEquals(true, stato.inRiproduzione)

        presenter.pausa()
        fake.emetti(StatoLettore(REGISTRAZIONE_ID, 500, inRiproduzione = false))
        advanceUntilIdle()
        stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(500, stato.posizioneMs)
        assertEquals(false, stato.inRiproduzione)
    }

    @Test
    fun `AC-188 un estratto riproduce i suoi intervalli in sequenza e si ferma alla fine dell ultimo`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, fake)
        val estratto = EstrattoRef(REGISTRAZIONE_ID, listOf(IntervalloMs(0, 1000), IntervalloMs(2000, 3000)))

        presenter.riproduciEstratto(estratto)
        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value).let {
            assertEquals(0, it.posizioneMs)
            assertEquals(true, it.inRiproduzione)
        }

        // the player advances through both intervals of the excerpt in sequence...
        fake.emetti(StatoLettore(REGISTRAZIONE_ID, 500, inRiproduzione = true))
        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value).let { assertEquals(500, it.posizioneMs) }

        // ...and stops at the end of the last one.
        fake.emetti(StatoLettore(REGISTRAZIONE_ID, 2000, inRiproduzione = false))
        advanceUntilIdle()
        val stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(2000, stato.posizioneMs)
        assertEquals(false, stato.inRiproduzione)
    }

    @Test
    fun `AC-189 sorgente non disponibile mostra un messaggio e non avvia la riproduzione`() = runTest {
        val fake = LettoreAudioFinta(nonDisponibili = setOf(REGISTRAZIONE_ID))
        val presenter = presentatore(this, fake)

        presenter.riproduci(REGISTRAZIONE_ID, 0)
        advanceUntilIdle()

        val stato = assertIs<LettoreUiStato.NonDisponibile>(presenter.stato.value)
        assertEquals(MESSAGGIO_SORGENTE_NON_DISPONIBILE, stato.messaggio)
        assertEquals(StatoLettore(null, 0, false), fake.stato.value)
    }

    @Test
    fun `AC-190 un nuovo play sostituisce la riproduzione in corso`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, fake)
        val altro = RegistrazioneId("id-2")

        presenter.riproduci(REGISTRAZIONE_ID, 0)
        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value)

        presenter.riproduci(altro, 0)
        assertEquals(LettoreUiStato.Caricamento, presenter.stato.value)
        advanceUntilIdle()

        assertEquals(StatoLettore(altro, 0, true), fake.stato.value)
        val stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(0, stato.posizioneMs)

        // a stray tick from the SUPERSEDED registrazione must not resurrect it in the presenter's stato
        fake.emetti(StatoLettore(REGISTRAZIONE_ID, 999, inRiproduzione = true))
        advanceUntilIdle()
        val dopo = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(0, dopo.posizioneMs)
    }

    @Test
    fun `MED-1 una richiesta identica alla precedente si risolve comunque`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, fake)

        presenter.riproduci(REGISTRAZIONE_ID, 500)
        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value)

        // identical request: `lettore.stato` already holds this exact value, so a StateFlow collector
        // alone never re-emits and would leave `_stato` stuck at Caricamento forever.
        presenter.riproduci(REGISTRAZIONE_ID, 500)
        advanceUntilIdle()

        val stato = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
        assertEquals(500, stato.posizioneMs)
        assertEquals(true, stato.inRiproduzione)
    }

    @Test
    fun `MED-2 un play verso una sorgente non disponibile mette in pausa la riproduzione in corso`() = runTest {
        val altro = RegistrazioneId("id-2")
        val fake = LettoreAudioFinta(nonDisponibili = setOf(altro))
        val presenter = presentatore(this, fake)

        presenter.riproduci(REGISTRAZIONE_ID, 500)
        advanceUntilIdle()
        assertIs<LettoreUiStato.Pronto>(presenter.stato.value)

        presenter.riproduci(altro, 0)
        advanceUntilIdle()

        assertIs<LettoreUiStato.NonDisponibile>(presenter.stato.value)
        // A is no longer left playing with no pause control: `lettore.pausa()` was called for it.
        val statoLettore = fake.stato.value
        assertEquals(REGISTRAZIONE_ID, statoLettore.registrazioneId)
        assertEquals(500, statoLettore.posizioneMs)
        assertEquals(false, statoLettore.inRiproduzione)
    }

    @Test
    fun `HIGH-2 una eccezione del lettore mostra un errore generico senza restare bloccato in Caricamento`() =
        runTest {
            val fake = LettoreAudioCheEsplode(
                eccezione = { IllegalStateException("linea audio non disponibile") },
                falliscePer = REGISTRAZIONE_ID,
            )
            val presenter = presentatore(this, fake)
            val altro = RegistrazioneId("id-2")

            presenter.riproduci(REGISTRAZIONE_ID, 0)
            advanceUntilIdle()

            val stato = assertIs<LettoreUiStato.NonDisponibile>(presenter.stato.value)
            assertEquals(MESSAGGIO_ERRORE_GENERICO, stato.messaggio)

            // the `stato` collector survived the fault: a later, successful request still resolves.
            presenter.riproduci(altro, 0)
            advanceUntilIdle()
            val dopo = assertIs<LettoreUiStato.Pronto>(presenter.stato.value)
            assertEquals(0, dopo.posizioneMs)
        }

    @Test
    fun `HIGH-2 una CancellationException del lettore non diventa un errore generico`() = runTest {
        val fake = LettoreAudioCheEsplode(eccezione = { CancellationException("annullato") })
        val presenter = presentatore(this, fake)

        presenter.riproduci(REGISTRAZIONE_ID, 0)
        advanceUntilIdle()

        // Rethrown, not swallowed: never turned into a NonDisponibile message.
        assertEquals(LettoreUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `HIGH-1 un comando lento non viene mai eseguito in concorrenza con uno piu recente`() {
        val eseguitori = Executors.newFixedThreadPool(4)
        val ioReale = eseguitori.asCoroutineDispatcher()
        try {
            val idA = RegistrazioneId("id-A")
            val idB = RegistrazioneId("id-B")
            val entrataA = CountDownLatch(1)
            val viaLiberaA = CountDownLatch(1)
            // Real concurrency detector: a lane serializes commands iff no thread ever finds `dentroDaA`
            // already true when it enters `riproduciDa` for a DIFFERENT id — the actual mechanism HIGH-1
            // must provide, checked directly rather than inferred from a completion-order race (which
            // would be flaky: which of two truly concurrent threads finishes last is timing luck, not a
            // property the test controls).
            val dentroDaA = java.util.concurrent.atomic.AtomicBoolean(false)
            val eseguitoInConcorrenza = java.util.concurrent.atomic.AtomicBoolean(false)
            val fake = object : LettoreAudio {
                private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
                override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

                override fun disponibile(id: RegistrazioneId) = true

                override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
                    if (id == idB && dentroDaA.get()) eseguitoInConcorrenza.set(true)
                    if (id == idA) {
                        dentroDaA.set(true)
                        entrataA.countDown()
                        assertTrue(viaLiberaA.await(5, TimeUnit.SECONDS), "timeout in attesa del via libera")
                    }
                    _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
                    if (id == idA) dentroDaA.set(false)
                }

                override fun riproduciEstratto(e: EstrattoRef): Nothing = error("non usato in questo test")

                override fun pausa() = Unit
            }
            val scope = CoroutineScope(SupervisorJob() + ioReale)
            val presenter = LettorePresenter(scope, ioReale, fake)

            presenter.riproduci(idA, 0)
            assertTrue(entrataA.await(5, TimeUnit.SECONDS), "A non e entrato in riproduciDa")

            // B is requested WHILE A is still in flight (slow/blocked) — on the real Dispatchers.IO,
            // without a lane, B's job would be free to enter `riproduciDa` concurrently with A's; give the
            // thread pool a generous window to schedule it before releasing A.
            presenter.riproduci(idB, 0)
            Thread.sleep(300)
            viaLiberaA.countDown()

            val statoFinale = attendiStato(presenter) { it is LettoreUiStato.Pronto }
            assertEquals(
                false,
                eseguitoInConcorrenza.get(),
                "riproduciDa(B) e entrato mentre riproduciDa(A) era ancora in corso",
            )
            val pronto = assertIs<LettoreUiStato.Pronto>(statoFinale)
            assertEquals(0, pronto.posizioneMs)
            // the port itself must end up reflecting B, never A's late completion "winning" the race.
            assertEquals(idB, fake.stato.value.registrazioneId)
        } finally {
            eseguitori.shutdownNow()
        }
    }

    /** Polls (real wall-clock, no virtual time here — see the HIGH-1 test) until [condizione] holds. */
    private fun attendiStato(
        presenter: LettorePresenter,
        timeoutMs: Long = 5_000,
        condizione: (LettoreUiStato) -> Boolean,
    ): LettoreUiStato {
        val scadenza = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < scadenza) {
            val attuale = presenter.stato.value
            if (condizione(attuale)) return attuale
            Thread.sleep(10)
        }
        error("timeout in attesa dello stato atteso; ultimo stato: ${presenter.stato.value}")
    }
}
