package snastro.ui

import androidx.compose.ui.graphics.Color
import snastro.kernel.VoceId
import snastro.ui.stile.SnastroColori

/**
 * AC-178 (contract kept) / AC-560: `palette(voceId, colori)` is deterministic — cycled by `numero`,
 * so the same Voce number always maps to the same color, for the life of a Trascritto (`VoceId` is
 * never reused/renumbered, dev-architecture `#valori-id`) — and now follows the active [SnastroColori]
 * theme instead of a fixed hardcoded set (AC-560 supersedes the old standalone `COLORI_VOCE`).
 * Plain function, not `@Composable`: callers already sit inside a composable and read
 * `snastro.ui.stile.LocalSnastroColori.current` themselves (dev-architecture `#pacchetti`, no new
 * composition-local plumbing needed here), which also keeps this trivially unit-testable.
 */
fun palette(voceId: VoceId, colori: SnastroColori): Color = colori.voci[(voceId.numero - 1).mod(colori.voci.size)]
