package snastro.ui.riassunto

/** One lambda per user action of the Riassunto tab (dev-architecture `#presenter`, user decision K-c). */
data class AzioniRiassunto(
    val cambiaArgomento: (String) -> Unit,
    val riassumi: () -> Unit,
    val scaricaModello: () -> Unit,
    val modificaLunghezzaMassima: () -> Unit,
    val cambiaLunghezzaMassima: (String) -> Unit,
    val salvaLunghezzaMassima: () -> Unit,
    val annullaLunghezzaMassima: () -> Unit,
    val apriModulo: () -> Unit = {},
    val chiudiModulo: () -> Unit = {},
)
