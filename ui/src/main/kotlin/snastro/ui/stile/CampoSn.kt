package snastro.ui.stile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PADDING_ORIZZONTALE_CAMPO = 10.dp

/**
 * AC-567: label (`caption`, weight 500) above, `raised` fill, 1dp `lineStrong` border,
 * `radiusControl`, helper/error (`caption`) below; in error the border and helper turn `danger`.
 */
@Suppress("LongParameterList") // one parameter per documented knob of the field (AC-567)
@Composable
public fun CampoSn(
    valore: String,
    onValoreCambiato: (String) -> Unit,
    modifier: Modifier = Modifier,
    etichetta: String? = null,
    aiuto: String? = null,
    errore: String? = null,
    placeholder: String? = null,
    abilitato: Boolean = true,
    piccolo: Boolean = false,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val coloriStato = coloriCampo(colori, abilitato, hasErrore = errore != null)
    val interazione = remember { MutableInteractionSource() }
    val focused by interazione.collectIsFocusedAsState()
    // mergeDescendants: the caller's modifier (width/weight/testTag) sits on this outer container
    // (review MED-7) and label+input+helper read as ONE semantics node. It does NOT make the caller's
    // testTag reach the BasicTextField's focus/set-text actions (a text field keeps its own node):
    // tests must target it with `hasSetTextAction() and hasAnyAncestor(hasTestTag(tag))`.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        if (etichetta != null) {
            Text(text = etichetta, style = etichettaCampoStile(tipografia), color = colori.inkMuted)
            Box(Modifier.height(SnastroMisure.space1))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (piccolo) SnastroMisure.controlS else SnastroMisure.controlM)
                .anelloFocus(colori.focus, focused, SnastroMisure.radiusControl)
                .background(coloriStato.sfondo, RoundedCornerShape(SnastroMisure.radiusControl))
                .border(1.dp, coloriStato.bordo, RoundedCornerShape(SnastroMisure.radiusControl))
                .padding(horizontal = PADDING_ORIZZONTALE_CAMPO),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (valore.isEmpty() && placeholder != null) {
                Text(text = placeholder, style = tipografia.body, color = colori.inkFaint)
            }
            BasicTextField(
                value = valore,
                onValueChange = onValoreCambiato,
                enabled = abilitato,
                singleLine = true,
                textStyle = tipografia.body.copy(color = coloriStato.testo),
                cursorBrush = SolidColor(colori.accentInk),
                interactionSource = interazione,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val sotto = errore ?: aiuto
        if (sotto != null) {
            Box(Modifier.height(SnastroMisure.space1))
            Text(
                text = sotto,
                style = tipografia.caption,
                color = if (errore != null) colori.danger else colori.inkMuted,
            )
        }
    }
}

/** AC-567: the label is `caption` at weight `Medium` (500) — the token, not the plain 400 `caption`. */
internal fun etichettaCampoStile(tipografia: SnastroTipografia): TextStyle =
    tipografia.caption.copy(fontWeight = FontWeight.Medium)

internal data class ColoriCampo(val sfondo: Color, val bordo: Color, val testo: Color)

/**
 * L705: a disabled field must look disabled (`sunken` fill, `inkFaint` border/text) — before this
 * fix `enabled = abilitato` reached the [BasicTextField] but the container kept its enabled colors.
 * An active error still wins over the disabled look (the two are not expected to coexist).
 */
internal fun coloriCampo(colori: SnastroColori, abilitato: Boolean, hasErrore: Boolean): ColoriCampo = when {
    hasErrore -> ColoriCampo(colori.raised, colori.danger, colori.ink)
    !abilitato -> ColoriCampo(colori.sunken, colori.inkFaint, colori.inkFaint)
    else -> ColoriCampo(colori.raised, colori.lineStrong, colori.ink)
}
