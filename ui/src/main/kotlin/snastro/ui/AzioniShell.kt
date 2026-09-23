package snastro.ui

/** One lambda per user action of the shell (dev-architecture `#presenter`, user decision K-c). */
data class AzioniShell(
    val apri: (String) -> Unit,
    val crea: (String, String) -> Unit,
    val chiudi: () -> Unit,
    val seleziona: (DestinazioneShell) -> Unit,
)
