package snastro.ui.lettore

import org.junit.jupiter.api.Test
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreAudio] (`tec-lettore-audio`, in-process): the fake below proves
 * it green on its own (D1); `:avvio`'s real `RiproduttoreWav` (AC-241) subclasses this too (D2). Only
 * pins the SYNCHRONOUS parts of the contract (every implementation sets `stato` before the call
 * returns) — the real-time advancing of `posizioneMs` while playing (both for a plain `riproduciDa`
 * and, interval by interval, for a `riproduciEstratto`) and stopping at the end of the last interval
 * are TIMED, over real playback time: they belong to `:avvio`'s real `RiproduttoreWav` (AC-241), not to
 * this in-process contract — exercised at the presenter level against the fake's own test-only
 * `emetti` ([LettorePresenterTest]).
 */
abstract class LettoreAudioContratto {
    protected abstract fun con(): LettoreAudio

    private val registrazioneId = RegistrazioneId("id-1")

    @Test
    fun `nessuna riproduzione e in corso all inizio`() {
        assertEquals(StatoLettore(null, 0, false), con().stato.value)
    }

    @Test
    fun `AC-187 riproduciDa avvia la riproduzione dalla posizione indicata`() {
        val lettore = con()
        lettore.riproduciDa(registrazioneId, 500)
        assertEquals(StatoLettore(registrazioneId, 500, true), lettore.stato.value)
    }

    @Test
    fun `AC-187 pausa ferma la riproduzione mantenendo la posizione`() {
        val lettore = con()
        lettore.riproduciDa(registrazioneId, 500)
        lettore.pausa()
        assertEquals(StatoLettore(registrazioneId, 500, false), lettore.stato.value)
    }

    @Test
    fun `MED-3 pausa senza riproduzione in corso non lancia e non cambia stato`() {
        val lettore = con()
        val prima = lettore.stato.value
        lettore.pausa()
        assertEquals(prima, lettore.stato.value)
    }

    @Test
    fun `AC-190 un nuovo riproduciDa sostituisce la riproduzione in corso`() {
        val lettore = con()
        val altro = RegistrazioneId("id-2")
        lettore.riproduciDa(registrazioneId, 500)
        lettore.riproduciDa(altro, 0)
        assertEquals(StatoLettore(altro, 0, true), lettore.stato.value)
    }

    @Test
    fun `MED-3 un riproduciEstratto sostituisce un riproduciDa in corso`() {
        val lettore = con()
        lettore.riproduciDa(registrazioneId, 500)
        val altro = RegistrazioneId("id-2")
        val estratto = EstrattoRef(altro, listOf(IntervalloMs(0, 1000)))
        lettore.riproduciEstratto(estratto)
        val stato = lettore.stato.value
        assertEquals(altro, stato.registrazioneId)
        assertEquals(0, stato.posizioneMs)
        assertTrue(stato.inRiproduzione)
    }

    @Test
    fun `AC-188 riproduciEstratto avvia la riproduzione per il registrazioneId dell estratto`() {
        val lettore = con()
        // MED-3: the first interval does NOT start at 0 (500) on purpose — `posizioneMs` is relative to
        // the REBUILT excerpt (StatoLettore's KDoc), never the absolute `inizioMs` of the source interval.
        val estratto = EstrattoRef(registrazioneId, listOf(IntervalloMs(500, 1000), IntervalloMs(2000, 3000)))
        lettore.riproduciEstratto(estratto)
        assertEquals(StatoLettore(registrazioneId, 0, true), lettore.stato.value)
    }

    @Test
    fun `AC-189 disponibile e vero per default`() {
        assertTrue(con().disponibile(registrazioneId))
    }
}
