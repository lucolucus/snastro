package snastro.ui.stile

/** AC-566: the three banner tones of the design system. */
public enum class TipoBanner { Info, Avviso, Errore }

/** AC-566: the banner's optional single action (at most one, review criterion). */
public data class AzioneBanner(val etichetta: String, val onClick: () -> Unit)
