package snastro.ui.stile

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DIAMETRO_GRANDE: Dp = 40.dp
private val ICONA_GRANDE: Dp = 18.dp
private val ICONA_PICCOLA: Dp = 14.dp
private val RAGGIO_PILLOLA: Dp = 999.dp

/**
 * AC-564: the round play/pause control — [grande] (40dp, `accent` fill, in the player) or compact
 * (`control-s`, `accentSoft` fill, on a row); disabled = `sunken` + `inkFaint`. Replaces every
 * text-glyph play/pause button (AC-559).
 */
@Composable
public fun BottonePlay(
    inRiproduzione: Boolean,
    onClick: () -> Unit,
    grande: Boolean,
    modifier: Modifier = Modifier,
    abilitato: Boolean = true,
) {
    val colori = LocalSnastroColori.current
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val focused by interazione.collectIsFocusedAsState()
    val diametro = if (grande) DIAMETRO_GRANDE else SnastroMisure.controlS
    val (sfondo, testo) = when {
        !abilitato -> colori.sunken to colori.inkFaint
        grande -> (if (hover) colori.accentHover else colori.accent) to colori.onAccent
        hover -> colori.accent to colori.onAccent
        else -> colori.accentSoft to colori.accentInk
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(diametro).anelloFocus(colori.focus, focused, RAGGIO_PILLOLA),
        enabled = abilitato,
        shape = SnastroMisure.radiusPill,
        color = sfondo,
        contentColor = testo,
        interactionSource = interazione,
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconaSn(
                icona = if (inRiproduzione) Icona.Pause else Icona.Play,
                descrizione = null,
                tinta = testo,
                dimensione = if (grande) ICONA_GRANDE else ICONA_PICCOLA,
            )
        }
    }
}
