package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.kernel.VoceId
import snastro.ui.formattaDurata

private val PADDING_ORIZZONTALE: Dp = 8.dp
private val PADDING_VERTICALE: Dp = 3.dp

/**
 * AC-S43: a Fonte chip after an element's own text — dot + current name, or a ring dot + "Voce n"
 * in [SnastroColori.inkMuted] when unattributed (same shapes as [PallinoVoce]/[EtichettaVoce], reused
 * here rather than redrawn), then the Segmento's `inizio` as [SnastroTipografia.timecode]
 * ([formattaDurata]: "1:05" under an hour, "1:02:05" from an hour on). `label`-sized text,
 * [SnastroMisure.radiusPill], [SnastroColori.sunken] fill, no border. **Not interactive** — a plain
 * (non-`onClick`) [Surface], so the semantics tree carries no click action (v1: out of scope, brief).
 */
@Composable
public fun FonteChip(voceId: Int, nome: String?, inizioMs: Long, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Surface(
        modifier = modifier,
        shape = SnastroMisure.radiusPill,
        color = colori.sunken,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE, vertical = PADDING_VERTICALE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
        ) {
            PallinoVoce(voceId = VoceId(voceId), conNome = nome != null)
            Text(
                text = nome ?: "Voce $voceId",
                style = tipografia.label,
                color = if (nome != null) colori.ink else colori.inkMuted,
            )
            Text(text = formattaDurata(inizioMs), style = tipografia.timecode, color = colori.inkMuted)
        }
    }
}
