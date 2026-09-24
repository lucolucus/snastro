package snastro.ui.modelli

/** One lambda per user action of S5 (dev-architecture `#presenter`, user decision K-c). */
data class AzioniModelli(val scarica: () -> Unit)
