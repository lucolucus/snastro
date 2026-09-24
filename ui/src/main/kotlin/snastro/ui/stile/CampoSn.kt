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
import androidx.compose.ui.graphics.SolidColor
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
    val bordo = if (errore != null) colori.danger else colori.lineStrong
    val interazione = remember { MutableInteractionSource() }
    val focused by interazione.collectIsFocusedAsState()
    Column {
        if (etichetta != null) {
            Text(text = etichetta, style = tipografia.caption, color = colori.inkMuted)
            Box(Modifier.height(SnastroMisure.space1))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (piccolo) SnastroMisure.controlS else SnastroMisure.controlM)
                .anelloFocus(colori.focus, focused, SnastroMisure.radiusControl)
                .background(colori.raised, RoundedCornerShape(SnastroMisure.radiusControl))
                .border(1.dp, bordo, RoundedCornerShape(SnastroMisure.radiusControl))
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
                textStyle = tipografia.body.copy(color = colori.ink),
                cursorBrush = SolidColor(colori.accentInk),
                interactionSource = interazione,
                modifier = modifier.fillMaxWidth(),
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
