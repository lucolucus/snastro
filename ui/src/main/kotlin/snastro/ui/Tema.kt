package snastro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The one Material 3 theme every screen wraps itself in (rule 11: what every screen shares).
 * Material 3's own default color scheme and typography (frugality rung 3, ADR 0001): nothing in
 * this feature's ACs asks for a custom palette/type scale yet — later blocks extend this call site,
 * never re-wrap [MaterialTheme] themselves.
 */
@Composable
fun SnastroTema(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
