package snastro.ui.lettore

import kotlinx.coroutines.flow.StateFlow
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId

/**
 * `tec-lettore-audio` (owned here, in-process, consumer-driven contract test —
 * [LettoreAudioContratto]): the shared audio player behind the "audio bar" / "▶" control. One source
 * plays at a time — a new `riproduciDa`/`riproduciEstratto` call REPLACES whatever is playing (AC-190).
 * Implemented by `:avvio` (`RiproduttoreWav` + WAV rebuild, AC-241) — this block only declares the
 * port, its fake and the presenter that consumes it.
 */
interface LettoreAudio {
    /** Whether [id]'s audio source can currently be played (AC-189 — false when the file moved/is missing). */
    fun disponibile(id: RegistrazioneId): Boolean

    /** Plays [id] from [daMs] (AC-187). Replaces any playback already in progress (AC-190). */
    fun riproduciDa(id: RegistrazioneId, daMs: Long)

    /** Plays [e]'s intervals in sequence, stopping at the end of the last one (AC-188). */
    fun riproduciEstratto(e: EstrattoRef)

    /** Pauses the current playback, if any — the position is kept, so a later `riproduciDa` can resume it. */
    fun pausa()

    /** Current playback state (AC-187: [StatoLettore.posizioneMs] advances while [StatoLettore.inRiproduzione]). */
    val stato: StateFlow<StatoLettore>
}
