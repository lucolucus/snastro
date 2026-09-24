package snastro.ui.stile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.kernel.VoceId
import snastro.ui.palette

private val DIAMETRO_NORMALE: Dp = 10.dp
private val DIAMETRO_GRANDE: Dp = 14.dp
private val SPESSORE_ANELLO: Dp = 2.dp

/**
 * AC-561: a filled dot when the Voce has a Nome, a 2dp ring (transparent inside) when it has
 * none — [snastro.ui.palette]'s voice colour, never used for text.
 */
@Composable
public fun PallinoVoce(voceId: VoceId, conNome: Boolean, grande: Boolean = false) {
    val colori = LocalSnastroColori.current
    val colore = palette(voceId, colori)
    val diametro = if (grande) DIAMETRO_GRANDE else DIAMETRO_NORMALE
    val base = Modifier.size(diametro)
    val decorato = if (conNome) {
        base.background(colore, CircleShape)
    } else {
        base.border(SPESSORE_ANELLO, colore, CircleShape)
    }
    Box(decorato)
}
