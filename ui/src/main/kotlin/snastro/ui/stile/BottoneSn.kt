package snastro.ui.stile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PADDING_ORIZZONTALE = 14.dp
private val PADDING_ORIZZONTALE_PICCOLO = 10.dp
private val PADDING_ORIZZONTALE_LINK = 6.dp
private val SCARTO_ICONA = 6.dp

private data class ColoriBottone(val sfondo: Color, val bordo: BorderStroke?, val testo: Color)

@Composable
private fun coloriBottone(
    colori: SnastroColori,
    variante: VarianteBottone,
    abilitato: Boolean,
    hover: Boolean,
): ColoriBottone {
    if (!abilitato) {
        return ColoriBottone(Color.Transparent, null, colori.inkFaint)
    }
    return when (variante) {
        VarianteBottone.Primario -> {
            val fondo = if (hover) colori.accentHover else colori.accent
            ColoriBottone(fondo, BorderStroke(1.dp, fondo), colori.onAccent)
        }
        VarianteBottone.Secondario -> {
            val fondo = if (hover) colori.sunken else colori.raised
            ColoriBottone(fondo, BorderStroke(1.dp, colori.lineStrong), colori.ink)
        }
        VarianteBottone.Fantasma -> {
            val fondo = if (hover) colori.sunken else Color.Transparent
            ColoriBottone(fondo, null, colori.ink)
        }
        VarianteBottone.Link -> ColoriBottone(Color.Transparent, null, colori.accentInk)
        VarianteBottone.Pericolo -> {
            val fondo = if (hover) colori.sunken else Color.Transparent
            ColoriBottone(fondo, null, colori.danger)
        }
    }
}

/**
 * AC-562: a text button with an optional leading icon, `control-m`/`control-s` tall, `radiusControl`
 * corners. Only one [VarianteBottone.Primario] per screen (review criterion — RC-1/RC-2 territory,
 * not mechanically checkable here).
 */
@Suppress("LongParameterList") // one parameter per documented knob of the button (AC-562)
@Composable
public fun BottoneSn(
    etichetta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variante: VarianteBottone = VarianteBottone.Secondario,
    piccolo: Boolean = false,
    icona: Icona? = null,
    abilitato: Boolean = true,
) {
    val colori = LocalSnastroColori.current
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val focused by interazione.collectIsFocusedAsState()
    val (sfondo, bordo, testo) = coloriBottone(colori, variante, abilitato, hover)
    val paddingOrizzontale = when {
        variante == VarianteBottone.Link -> PADDING_ORIZZONTALE_LINK
        piccolo -> PADDING_ORIZZONTALE_PICCOLO
        else -> PADDING_ORIZZONTALE
    }
    Surface(
        onClick = onClick,
        modifier = modifier.anelloFocus(colori.focus, focused, SnastroMisure.radiusControl),
        enabled = abilitato,
        shape = forma,
        color = sfondo,
        contentColor = testo,
        border = bordo,
        interactionSource = interazione,
    ) {
        Row(
            modifier = Modifier
                .height(if (piccolo) SnastroMisure.controlS else SnastroMisure.controlM)
                .padding(horizontal = paddingOrizzontale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SCARTO_ICONA, Alignment.CenterHorizontally),
        ) {
            if (icona != null) {
                IconaSn(icona = icona, descrizione = null, tinta = testo, dimensione = SnastroMisure.iconS)
            }
            Text(text = etichetta, style = LocalSnastroTipografia.current.label)
        }
    }
}
