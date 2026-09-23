package snastro.ui

import androidx.compose.ui.graphics.Color
import snastro.kernel.VoceId

/**
 * AC-178: `palette(voceId)` is deterministic — a fixed, mutually distinguishable set of colors
 * cycled by `numero`, so the same Voce number always maps to the same color, for the life of a
 * Trascritto (`VoceId` is never reused/renumbered, dev-architecture `#valori-id`).
 */
@Suppress("MagicNumber") // named hex colors of a fixed palette, not magic values
private val COLORI_VOCE: List<Color> = listOf(
    Color(0xFF1E88E5), // blu
    Color(0xFFD81B60), // magenta
    Color(0xFF43A047), // verde
    Color(0xFFF4511E), // arancione
    Color(0xFF8E24AA), // viola
    Color(0xFF00897B), // verde acqua
    Color(0xFF6D4C41), // marrone
    Color(0xFF3949AB), // indaco
)

/** AC-178: the color for [voceId], stable for the same "Voce n" number. */
fun palette(voceId: VoceId): Color = COLORI_VOCE[(voceId.numero - 1).mod(COLORI_VOCE.size)]
