package snastro.ui.stile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val TOOLTIP_NUMERO_PERSONE = "Quante persone parlano? Da 1 a 10, oppure lascia vuoto"
private const val PLACEHOLDER_NUMERO_PERSONE = "auto"
private val LARGHEZZA_CAMPO = 72.dp

/**
 * AC-567: the "Quante persone parlano?" field — 72dp wide, centred, tabular, `control-s` in rows,
 * no visible label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun CampoNumeroPersone(
    valore: String,
    onValoreCambiato: (String) -> Unit,
    modifier: Modifier = Modifier,
    errore: Boolean = false,
    abilitato: Boolean = true,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val bordo = if (errore) colori.danger else colori.lineStrong
    val interazione = remember { MutableInteractionSource() }
    val focused by interazione.collectIsFocusedAsState()
    // mergeDescendants: the caller's modifier (width/testTag) sits on TooltipArea (review MED-7) and
    // this box reads as ONE semantics node. It does NOT make the caller's testTag reach the
    // BasicTextField's focus/set-text actions (a text field keeps its own node): tests must target
    // it with `hasSetTextAction() and hasAnyAncestor(hasTestTag(tag))`.
    TooltipArea(tooltip = { Suggerimento() }, modifier = modifier) {
        Box(
            modifier = Modifier
                .width(LARGHEZZA_CAMPO)
                .height(SnastroMisure.controlS)
                .anelloFocus(colori.focus, focused, SnastroMisure.radiusControl)
                .background(colori.raised, RoundedCornerShape(SnastroMisure.radiusControl))
                .border(1.dp, bordo, RoundedCornerShape(SnastroMisure.radiusControl))
                .padding(horizontal = SnastroMisure.space2)
                .semantics(mergeDescendants = true) { contentDescription = TOOLTIP_NUMERO_PERSONE },
            contentAlignment = Alignment.Center,
        ) {
            if (valore.isEmpty()) {
                Text(
                    text = PLACEHOLDER_NUMERO_PERSONE,
                    style = tipografia.body,
                    color = colori.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }
            BasicTextField(
                value = valore,
                onValueChange = onValoreCambiato,
                enabled = abilitato,
                singleLine = true,
                textStyle = tipografia.body.copy(
                    color = colori.ink,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                ),
                cursorBrush = SolidColor(colori.accentInk),
                interactionSource = interazione,
            )
        }
    }
}

@Composable
private fun Suggerimento() {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
    ) {
        Text(
            text = TOOLTIP_NUMERO_PERSONE,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(SnastroMisure.space2),
        )
    }
}
