package snastro.ui.impostazioni

/** One lambda per user action of Impostazioni › Riassunto. */
data class AzioniLunghezzaRiassunto(
    val cambia: (testo: String) -> Unit,
    val salva: () -> Unit,
    val ripristina: () -> Unit,
)
