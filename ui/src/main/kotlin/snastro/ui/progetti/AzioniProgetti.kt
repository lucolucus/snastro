package snastro.ui.progetti

/** One lambda per user action of S1 · Progetti (dev-architecture `#presenter`, user decision K-c). */
data class AzioniProgetti(
    val crea: (cartellaGenitore: String, nome: String) -> Unit,
    val apri: (percorso: String) -> Unit,
    val chiudiErroreCrea: () -> Unit,
    val chiudiErroreApri: () -> Unit,
)
