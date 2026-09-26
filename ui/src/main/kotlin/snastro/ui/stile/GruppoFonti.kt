package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * AC-S44: [fonti] laid out as a group of [FonteChip]s, [SnastroMisure.space2] gaps in both
 * directions, wrapping to the next line instead of clipping when the row runs out of width — a
 * [FlowRow], not a [androidx.compose.foundation.layout.Row]. Callers place this right after an
 * element's own text (the `space-2` gap is between that text and the group too, per the design
 * system delta); the group itself only lays out the chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun GruppoFonti(fonti: List<FonteChipDati>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        fonti.forEachIndexed { indice, fonte ->
            FonteChip(
                voceId = fonte.voceId,
                nome = fonte.nome,
                inizioMs = fonte.inizioMs,
                modifier = Modifier.testTag("fonte-chip-$indice"),
            )
        }
    }
}
