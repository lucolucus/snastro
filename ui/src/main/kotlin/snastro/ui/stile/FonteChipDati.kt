package snastro.ui.stile

/**
 * AC-S44: one [FonteChip]'s own data — [GruppoFonti]'s input shape, Published-Language-free (a
 * plain `:ui` view type, not `:sintesi:applicazione`'s `FonteVista`): a screen maps its own read
 * model into this before calling [GruppoFonti].
 */
public data class FonteChipDati(val voceId: Int, val nome: String?, val inizioMs: Long)
