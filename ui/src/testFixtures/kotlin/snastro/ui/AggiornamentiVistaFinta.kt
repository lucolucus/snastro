package snastro.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fake [AggiornamentiVista] (RC-9): filling in the missing test double for this `tec-shell-ui` port —
 * `schermata-registrazioni` is its first real consumer. [emetti] pushes a [Cambiamento] the way a real
 * after-commit subscriber would (R15).
 */
class AggiornamentiVistaFinta : AggiornamentiVista {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(extraBufferCapacity = 8)
    override val cambiamenti: Flow<Cambiamento> = _cambiamenti

    /** Test-only: simulates a change notification. */
    fun emetti(cambiamento: Cambiamento) {
        check(_cambiamenti.tryEmit(cambiamento)) { "AggiornamentiVistaFinta: buffer pieno" }
    }
}
