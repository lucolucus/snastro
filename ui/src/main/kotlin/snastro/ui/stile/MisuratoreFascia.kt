package snastro.ui.stile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.parlanti.applicazione.porte.Fascia

private val LARGHEZZA_TACCA: Dp = 12.dp
private val ALTEZZA_TACCA: Dp = 6.dp
private val RAGGIO_TACCA: Dp = 2.dp
private val SCARTO: Dp = 6.dp
private const val TACCHE_TOTALI = 2

/**
 * AC-569: two 12×6dp pips + the word, never a number (INV-20) — `forte` fills both pips (`ink`
 * word), `debole` fills the first (`inkMuted` word), `nessuna` fills none (`inkMuted` word). Reuses
 * the existing [Fascia] read-model value (frugality rung 2), not a new `stile`-owned enum.
 */
@Composable
public fun MisuratoreFascia(fascia: Fascia, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    val tacchePiene = when (fascia) {
        Fascia.FORTE -> 2
        Fascia.DEBOLE -> 1
        Fascia.NESSUNA -> 0
    }
    val coloreParola = if (fascia == Fascia.FORTE) colori.ink else colori.inkMuted
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SCARTO),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(RAGGIO_TACCA)) {
            repeat(TACCHE_TOTALI) { indice -> Tacca(riempita = indice < tacchePiene) }
        }
        Text(text = fascia.name.lowercase(), style = LocalSnastroTipografia.current.caption, color = coloreParola)
    }
}

@Composable
private fun Tacca(riempita: Boolean) {
    val colori = LocalSnastroColori.current
    val riempimento = if (riempita) colori.accent else colori.sunken
    val bordo = if (riempita) colori.accent else colori.line
    Box(
        Modifier.size(width = LARGHEZZA_TACCA, height = ALTEZZA_TACCA)
            .background(riempimento, RoundedCornerShape(RAGGIO_TACCA))
            .border(1.dp, bordo, RoundedCornerShape(RAGGIO_TACCA)),
    )
}
