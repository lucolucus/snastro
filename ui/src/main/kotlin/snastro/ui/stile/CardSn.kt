package snastro.ui.stile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * AC-568: `raised` + 1dp `line` border + `radiusCard` + `space4` padding, NO shadow (`shadow-pop`
 * is reserved for menus/dialogs/the selection toolbar).
 */
@Composable
public fun CardSn(modifier: Modifier = Modifier, contenuto: @Composable ColumnScope.() -> Unit) {
    val colori = LocalSnastroColori.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        color = colori.raised,
        contentColor = colori.ink,
        border = BorderStroke(1.dp, colori.line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(SnastroMisure.space4), content = contenuto)
    }
}
