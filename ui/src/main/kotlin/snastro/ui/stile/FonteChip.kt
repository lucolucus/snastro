package snastro.ui.stile

import androidx.compose.foundation.clickable
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
    FonteChip(FonteChipDati(voceId, nome, inizioMs), onClick = null, modifier = modifier)
}

/**
 * The Incontro form of [FonteChip] ([FonteChipDati]'s additions): the same layout, with an optional
 * voice part and a [FonteChipDati.tempoTesto] in place of the bare timecode. [onClick] `null` = not
 * interactive (a plain [Surface] with no click action).
 */
@Composable
public fun FonteChip(dati: FonteChipDati, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val voceId = dati.voceId
    val nomeVisibile = if (dati.voceNonPresente) null else dati.nome
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = SnastroMisure.radiusPill,
        color = colori.sunken,
    ) {
        RigaChip(
            modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE, vertical = PADDING_VERTICALE),
            pallino = if (voceId != null && !dati.voceNonPresente) {
                { PallinoVoce(voceId = VoceId(voceId), conNome = nomeVisibile != null) }
            } else {
                null
            },
            nome = if (voceId != null) {
                {
                    Text(
                        text = when {
                            dati.voceNonPresente -> "Voce $voceId · $TESTO_NON_PIU_PRESENTE"
                            else -> nomeVisibile ?: "Voce $voceId"
                        },
                        style = tipografia.label,
                        color = if (nomeVisibile != null) colori.ink else colori.inkMuted,
                    )
                }
            } else {
                null
            },
            timecode = {
                Text(
                    text = dati.tempoTesto,
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
    pallino: (@Composable () -> Unit)?,
    nome: (@Composable () -> Unit)?,
    timecode: @Composable () -> Unit,
) {
    val vuoto: @Composable () -> Unit = {}
    Layout(contents = listOf(pallino ?: vuoto, nome ?: vuoto, timecode), modifier = modifier) { misure, vincoli ->
        val (misurePallino, misureNome, misureTimecode) = misure
        val scarto = SnastroMisure.space1.roundToPx()
        val sciolti = vincoli.copy(minWidth = 0, minHeight = 0)
        val pallinoP = misurePallino.singleOrNull()?.measure(sciolti)
        val timecodeP = misureTimecode.single().measure(sciolti)
        val larghezzaPallino = pallinoP?.let { it.width + scarto } ?: 0
        val larghezzaFissa = larghezzaPallino + timecodeP.width + scarto
        val vincoliNome = if (vincoli.hasBoundedWidth) {
            sciolti.copy(maxWidth = (vincoli.maxWidth - larghezzaFissa).coerceAtLeast(0))
        } else {
            sciolti.copy(maxWidth = Constraints.Infinity)
        }
        val nomeP = misureNome.singleOrNull()?.measure(vincoliNome)
        val larghezzaNome = nomeP?.let { it.width + scarto } ?: 0
        val larghezzaTotale = larghezzaPallino + larghezzaNome + timecodeP.width
        val altezza = maxOf(pallinoP?.height ?: 0, nomeP?.height ?: 0, timecodeP.height)
        layout(larghezzaTotale, altezza) {
            var x = 0
            pallinoP?.let {
                it.placeRelative(x, (altezza - it.height) / 2)
                x += larghezzaPallino
            }
            nomeP?.let {
                it.placeRelative(x, (altezza - it.height) / 2)
                x += larghezzaNome
            }
            timecodeP.placeRelative(x, (altezza - timecodeP.height) / 2)
        }
    }
}

internal const val TAG_FONTE_CHIP_TIMECODE: String = "fonte-chip-timecode"
