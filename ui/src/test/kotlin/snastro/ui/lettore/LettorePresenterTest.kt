package snastro.ui.lettore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val REGISTRAZIONE_ID = RegistrazioneId("id-1")

@OptIn(ExperimentalCoroutinesApi::class)
class LettorePresenterTest {
    private fun presentatore(scope: TestScope, lettore: LettoreAudioFinta = LettoreAudioFinta()): LettorePresenter {
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
}
