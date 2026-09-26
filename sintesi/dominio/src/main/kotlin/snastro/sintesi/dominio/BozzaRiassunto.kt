package snastro.sintesi.dominio

/** The raw, UNVERIFIED answer of the model; texts in the `{V<n>}` form. [Riassunto.completa] verifies it (INV-S4). */
public data class BozzaRiassunto(
    val sommario: String?,
    val decisioni: List<BozzaElemento>,
    val questioniAperte: List<BozzaElemento>,
    val azioni: List<BozzaElemento>,
    val puntiChiave: List<BozzaElemento>,
)
