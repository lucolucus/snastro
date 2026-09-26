package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 *
 * AC-S44 rework 1: [nome] carries no length cap, so on a narrow row it can need to wrap. Row
 * measures unweighted children in order, each against the FULL remaining width — so an unweighted
 * name `Text` would claim all the space before the timecode is even measured, leaving it squeezed
 * into a near-zero-width column (wrapped one character per line instead of staying one line; the
 * PNGs showed it as a stray character escaping the pill). `weight(1f, fill = false)` makes the name
 * the ONE flexible child: Row measures the dot and the timecode first (their natural, one-line
 * sizes), then gives the name only what's left over — so the timecode always keeps its own natural
 * size and the pill wraps the name as a whole instead.
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
                modifier = Modifier.weight(weight = 1f, fill = false),
                style = tipografia.label,
                color = if (nome != null) colori.ink else colori.inkMuted,
            )
            Text(
                text = formattaDurata(inizioMs),
                modifier = Modifier.testTag(TAG_FONTE_CHIP_TIMECODE),
                style = tipografia.timecode,
                color = colori.inkMuted,
            )
        }
    }
}

internal const val TAG_FONTE_CHIP_TIMECODE: String = "fonte-chip-timecode"
