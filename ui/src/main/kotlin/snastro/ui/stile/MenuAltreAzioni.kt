package snastro.ui.stile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import snastro.ui.testi.ETICHETTA_ALTRE_AZIONI

/** The "⋯" (`More`) button and its menu; [contenuto] receives the `chiudi` that also returns focus to the button. */
@Composable
internal fun MenuAltreAzioni(
    tag: String,
    abilitato: Boolean,
    contenuto: @Composable ColumnScope.(chiudi: () -> Unit) -> Unit,
) {
    var espanso by remember { mutableStateOf(false) }
    // L742b (S4 precedent): focus returns to this same button once the menu closes.
    val richiestaFocus = remember { FocusRequester() }
    fun chiudi() {
        espanso = false
        richiestaFocus.requestFocus()
    }
    Box {
        BottoneIconaSn(
            icona = Icona.More,
            descrizione = ETICHETTA_ALTRE_AZIONI,
            onClick = { espanso = true },
            abilitato = abilitato,
            modifier = Modifier.focusRequester(richiestaFocus).testTag(tag),
        )
        DropdownMenu(expanded = espanso, onDismissRequest = ::chiudi) { contenuto(::chiudi) }
    }
}
