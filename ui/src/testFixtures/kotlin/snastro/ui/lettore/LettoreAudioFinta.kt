package snastro.ui.lettore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId

/**
 * Fake [LettoreAudio] (RC-9): `disponibile` is false only for [nonDisponibili]; `riproduciDa` /
 * `riproduciEstratto` set [stato] synchronously. [emetti] is test-only (beyond the port): it lets a
 * test simulate the underlying player reporting a later tick — position advancing (AC-187), or an
 * estratto reaching the end of its last interval (AC-188) — the way the real `RiproduttoreWav`
 * (`:avvio`, AC-241) would, asynchronously, over real playback time.
 */
class LettoreAudioFinta(private val nonDisponibili: Set<RegistrazioneId> = emptySet()) : LettoreAudio {
    private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
    override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

    override fun disponibile(id: RegistrazioneId): Boolean = id !in nonDisponibili

    override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
        _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
    }

    override fun riproduciEstratto(e: EstrattoRef) {
        _stato.value = StatoLettore(e.registrazioneId, e.intervalli.first().inizioMs, inRiproduzione = true)
    }

    override fun pausa() {
        _stato.update { it.copy(inRiproduzione = false) }
    }

    /** Test-only: simulates the player reporting a later tick (see class KDoc). */
    fun emetti(stato: StatoLettore) {
        _stato.value = stato
    }
}
