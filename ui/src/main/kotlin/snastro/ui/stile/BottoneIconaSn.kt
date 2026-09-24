package snastro.ui.stile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * AC-563: a square icon-only button, `control-m`/`control-s`, hover `sunken`; [descrizione] is
 * both its tooltip and its accessible name.
 */
@Suppress("LongParameterList") // one parameter per documented knob of the button (AC-563)
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun BottoneIconaSn(
    icona: Icona,
    descrizione: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    piccolo: Boolean = false,
    abilitato: Boolean = true,
) {
    val colori = LocalSnastroColori.current
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val focused by interazione.collectIsFocusedAsState()
    val sfondo = if (abilitato && hover) colori.sunken else Color.Transparent
    val testo = if (abilitato) colori.ink else colori.inkFaint
    val dimensione = if (piccolo) SnastroMisure.controlS else SnastroMisure.controlM
    TooltipArea(tooltip = { Etichetta(descrizione) }) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(dimensione).anelloFocus(colori.focus, focused, SnastroMisure.radiusControl),
            enabled = abilitato,
            shape = forma,
            color = sfondo,
            contentColor = testo,
            interactionSource = interazione,
        ) {
            Box(contentAlignment = Alignment.Center) {
                IconaSn(icona = icona, descrizione = descrizione, tinta = testo, dimensione = SnastroMisure.iconS)
            }
        }
    }
}

@Composable
private fun Etichetta(testo: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
    ) {
        Text(
            text = testo,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(SnastroMisure.space2),
        )
    }
}
