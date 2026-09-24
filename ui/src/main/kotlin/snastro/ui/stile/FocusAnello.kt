package snastro.ui.stile

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SPESSORE_FOCUS: Dp = 2.dp
private val DISTACCO_FOCUS: Dp = 2.dp

/**
 * AC-570: the 2dp `focus` ring, offset 2dp outside the component's own bounds — every interactive
 * `stile` component draws it when keyboard-focused, never a Material default focus indication.
 */
public fun Modifier.anelloFocus(colore: Color, attivo: Boolean, raggio: Dp): Modifier =
    if (!attivo) {
        this
    } else {
        this.drawWithContent {
            drawContent()
            val distacco = DISTACCO_FOCUS.toPx()
            val spessore = SPESSORE_FOCUS.toPx()
            drawRoundRect(
                color = colore,
                topLeft = Offset(-distacco, -distacco),
                size = Size(size.width + 2 * distacco, size.height + 2 * distacco),
                cornerRadius = CornerRadius(raggio.toPx() + distacco),
                style = Stroke(width = spessore),
            )
        }
    }
