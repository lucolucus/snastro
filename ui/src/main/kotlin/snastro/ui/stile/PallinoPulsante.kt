package snastro.ui.stile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

internal const val DURATA_MEZZA_PULSAZIONE_MS: Int = 800
internal const val ALPHA_MINIMA_PULSAZIONE: Float = 0.35f

/**
 * AC-565/AC-S45: the 1.6s round-trip alpha pulse (0.8s each leg) shared by every "in corso" dot of
 * the design system — a constant dot when [LocalRiduciMovimento] is on, never a running
 * `rememberInfiniteTransition` inside a headless render-check fixture. Extracted so [ChipStato]'s
 * `InCorso` dot and [SchedeSn]'s [SegnoScheda.InCorso] mark share one animation instead of two
 * (frugality: same rule, one owner).
 */
@Composable
internal fun PallinoPulsante(colore: Color, diametro: Dp, modifier: Modifier = Modifier) {
    val riduciMovimento = LocalRiduciMovimento.current
    val alpha = if (riduciMovimento) {
        1f
    } else {
        val transizione = rememberInfiniteTransition(label = "pallino-pulsante")
        val valoreAnimato by transizione.animateFloat(
            initialValue = 1f,
            targetValue = ALPHA_MINIMA_PULSAZIONE,
            animationSpec = infiniteRepeatable(
                animation = tween(DURATA_MEZZA_PULSAZIONE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha-pallino-pulsante",
        )
        valoreAnimato
    }
    Box(modifier.size(diametro).alpha(alpha).background(colore, CircleShape))
}
