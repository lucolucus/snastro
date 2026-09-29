package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign

/**
 * AC-565-style shared "nothing here yet" pattern (pre-release finding #152, rework): a centered
 * [messaggio], with an optional primary [azione] slot right below it — the Riassunto tab's states 1
 * ("model not installed") and 4 ("no Riassunto yet", ux-proposal rows 1/4) are its first callers.
 */
@Composable
public fun EmptyState(messaggio: String, modifier: Modifier = Modifier, azione: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_EMPTY_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
    ) {
        Text(
            text = messaggio,
            style = LocalSnastroTipografia.current.body,
            color = LocalSnastroColori.current.inkMuted,
            textAlign = TextAlign.Center,
        )
        azione?.invoke()
    }
}

internal const val TAG_EMPTY_STATE: String = "empty-state"
