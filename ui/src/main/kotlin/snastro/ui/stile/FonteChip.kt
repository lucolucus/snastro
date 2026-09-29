package snastro.ui.stile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
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
 * AC-S44 rework 1: [nome] carries no length cap, so on a narrow row it can need to wrap — the dot and
 * timecode keep their own natural, one-line size, [nome] gets only what's left over.
 *
 * AC-S44 rework 2 (pre-release finding #113): a plain `Row` + `Modifier.weight(1f, fill = false)`
 * would give [nome] "what's left over" ONLY under a bounded-width parent — under an UNBOUNDED one
 * ([androidx.compose.foundation.lazy.LazyRow]/[androidx.compose.foundation.horizontalScroll] on the
 * same axis, which hand children `Constraints.Infinity`) Compose's weight machinery cannot divide an
 * infinite remainder and [nome] collapses to zero width instead of wrapping (`FonteChipTest`'s own
 * regression once reproduced it). [RigaChip] below measures the SAME "leftover space" idea by hand
 * instead, so it degrades to [nome]'s own natural width when the incoming constraints are unbounded,
 * rather than relying on `weight` at all — this control now works under ANY ancestor.
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
        RigaChip(
            modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE, vertical = PADDING_VERTICALE),
            pallino = { PallinoVoce(voceId = VoceId(voceId), conNome = nome != null) },
            nome = {
                Text(
                    text = nome ?: "Voce $voceId",
                    style = tipografia.label,
                    color = if (nome != null) colori.ink else colori.inkMuted,
                )
            },
            timecode = {
                Text(
                    text = formattaDurata(inizioMs),
                    modifier = Modifier.testTag(TAG_FONTE_CHIP_TIMECODE),
                    style = tipografia.timecode,
                    color = colori.inkMuted,
                )
            },
        )
    }
}

/**
 * [pallino] and [timecode] measured first, at their own natural (loose) size; [nome] then gets
 * `incoming.maxWidth - (their width + two [SnastroMisure.space1] gaps)` when the incoming width is
 * BOUNDED, or its own natural (unconstrained) width when it is not — never a `weight` division of an
 * infinite remainder. All three centered on the row's cross axis, laid out left to right.
 */
@Composable
private fun RigaChip(
    modifier: Modifier,
    pallino: @Composable () -> Unit,
    nome: @Composable () -> Unit,
    timecode: @Composable () -> Unit,
) {
    Layout(contents = listOf(pallino, nome, timecode), modifier = modifier) { misure, vincoli ->
        val (misurePallino, misureNome, misureTimecode) = misure
        val scarto = SnastroMisure.space1.roundToPx()
        val sciolti = vincoli.copy(minWidth = 0, minHeight = 0)
        val pallinoP = misurePallino.single().measure(sciolti)
        val timecodeP = misureTimecode.single().measure(sciolti)
        val larghezzaFissa = pallinoP.width + scarto + timecodeP.width + scarto
        val vincoliNome = if (vincoli.hasBoundedWidth) {
            sciolti.copy(maxWidth = (vincoli.maxWidth - larghezzaFissa).coerceAtLeast(0))
        } else {
            sciolti.copy(maxWidth = Constraints.Infinity)
        }
        val nomeP = misureNome.single().measure(vincoliNome)
        val larghezzaTotale = pallinoP.width + scarto + nomeP.width + scarto + timecodeP.width
        val altezza = maxOf(pallinoP.height, nomeP.height, timecodeP.height)
        layout(larghezzaTotale, altezza) {
            var x = 0
            pallinoP.placeRelative(x, (altezza - pallinoP.height) / 2)
            x += pallinoP.width + scarto
            nomeP.placeRelative(x, (altezza - nomeP.height) / 2)
            x += nomeP.width + scarto
            timecodeP.placeRelative(x, (altezza - timecodeP.height) / 2)
        }
    }
}

internal const val TAG_FONTE_CHIP_TIMECODE: String = "fonte-chip-timecode"
