package snastro.ui.impostazioni

/**
 * The lunghezza massima typed in either editor (Impostazioni › Riassunto and the Riassunto tab's inline one, AC-S138):
 * the number in [testo], surrounding spaces ignored, if it lies in [minimo]..[massimo]; `null` otherwise, so no
 * command is sent. The bounds come from `impostazioni-sintesi`, never hardcoded.
 */
internal fun lunghezzaMassimaDa(testo: String, minimo: Int, massimo: Int): Int? =
    testo.trim().toIntOrNull()?.takeIf { it in minimo..massimo }
