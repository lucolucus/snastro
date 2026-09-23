package snastro.ui.lettore

import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId

/** One lambda per user action of the shared player (dev-architecture `#presenter`, user decision K-c). */
data class AzioniLettore(
    val riproduci: (RegistrazioneId, Long) -> Unit,
    val riproduciEstratto: (EstrattoRef) -> Unit,
    val pausa: () -> Unit,
)
