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
 * returns) — the real-time advancing of `posizioneMs` while playing is a presenter-level concern,
 * exercised there against the fake's own test-only `emetti` ([LettorePresenterTest]).
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
    fun `AC-190 un nuovo riproduciDa sostituisce la riproduzione in corso`() {
        val lettore = con()
        val altro = RegistrazioneId("id-2")
        lettore.riproduciDa(registrazioneId, 500)
        lettore.riproduciDa(altro, 0)
        assertEquals(StatoLettore(altro, 0, true), lettore.stato.value)
    }

    @Test
    fun `AC-188 riproduciEstratto avvia la riproduzione per il registrazioneId dell estratto`() {
        val lettore = con()
        val estratto = EstrattoRef(registrazioneId, listOf(IntervalloMs(0, 1000), IntervalloMs(2000, 3000)))
        lettore.riproduciEstratto(estratto)
        val stato = lettore.stato.value
        assertEquals(registrazioneId, stato.registrazioneId)
        assertTrue(stato.inRiproduzione)
    }

    @Test
    fun `AC-189 disponibile e vero per default`() {
        assertTrue(con().disponibile(registrazioneId))
    }
}
