package snastro.ui.stile

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

private val ALTEZZA_SCHEDA = 28.dp
private val PADDING_TRACCIA = 3.dp
private val DIAMETRO_PALLINO_SEGNO = 6.dp

/**
 * AC-S45: the segmented tab control (a `sunken` track, the selected tab raised with a 1dp
 * shadow — same chrome the Voci panel already drew for its one always-selected tab, now moved
 * here and generalized to several with a real selection). Each tab is a real accessibility tab
 * ([Role.Tab] via [androidx.compose.foundation.selection.selectable]) with the [AC-570][anelloFocus]
 * keyboard focus ring; [segni] renders one optional trailing [SegnoScheda] mark per tab index.
 */
@Composable
public fun SchedeSn(
    schede: List<String>,
    selezionata: Int,
    onSeleziona: (Int) -> Unit,
    segni: Map<Int, SegnoScheda> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val colori = LocalSnastroColori.current
    Surface(
        color = colori.sunken,
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
        modifier = modifier.fillMaxWidth(),
    ) {
        // AC-116 (rework): a SINGLE tab has nothing to switch to — [interattiva] `false` then drops
        // the Tab role/selectable/focus ring below, so it stops being a dead keyboard stop (the Voci
        // panel's own one-tab header, `SchermataPannelloVoci.kt`, is this control's first such caller).
        val interattiva = schede.size > 1
        Row(modifier = Modifier.padding(PADDING_TRACCIA).fillMaxWidth().selectableGroup()) {
            schede.forEachIndexed { indice, testo ->
                SchedaSn(
                    indice = indice,
                    testo = testo,
                    selezionata = indice == selezionata,
                    segno = segni[indice],
                    interattiva = interattiva,
                    onClick = { onSeleziona(indice) },
                    modifier = Modifier.weight(1f).testTag("scheda-$indice"),
                )
            }
        }
    }
}

@Suppress("LongParameterList") // one parameter per tab's own render input, [indice] only to tag its [SegnoScheda] mark
@Composable
private fun SchedaSn(
    indice: Int,
    testo: String,
    selezionata: Boolean,
    segno: SegnoScheda?,
    interattiva: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colori = LocalSnastroColori.current
    val interazione = remember { MutableInteractionSource() }
    val focused by interazione.collectIsFocusedAsState()
    val sfondo = if (selezionata) colori.raised else Color.Transparent
    val testoColore = if (selezionata) colori.ink else colori.inkMuted
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    Surface(
        // AC-115 (rework): the ring is drawn OUTSIDE the tab's own bounds (`anelloFocus` paints past
        // them by design) so it must sit OUTSIDE the clip below, never covered by the raised
        // neighbour's own shadow; the `clip(forma)` — applied BEFORE `selectable` — confines the
        // ripple/indication [selectable] paints to the SAME rounded shape [Surface] itself uses,
        // instead of the ripple bleeding past the corners as a full rectangle.
        modifier = modifier
            .anelloFocus(colori.focus, focused, SnastroMisure.radiusControl)
            .clip(forma)
            .then(
                // AC-116: no real selection with a single tab — plain, non-focusable chrome only.
                if (interattiva) {
                    Modifier.selectable(
                        selected = selezionata,
                        interactionSource = interazione,
                        indication = LocalIndication.current,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                // AC-114 (rework): the queued/running mark is otherwise silent to a screen reader —
                // the tab's own selectable semantics gain a stateDescription naming it.
                if (segno != null) Modifier.semantics { stateDescription = descrizioneSegno(segno) } else Modifier,
            ),
        shape = forma,
        color = sfondo,
        contentColor = testoColore,
        shadowElevation = if (selezionata) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.height(ALTEZZA_SCHEDA).padding(horizontal = SnastroMisure.space2),
            horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = testo, style = LocalSnastroTipografia.current.label, color = testoColore)
            segno?.let { SegnoSchedaVista(it, testoColore, Modifier.testTag("scheda-$indice-segno")) }
        }
    }
}

/** AC-114 (rework): the [SegnoScheda] mark's own accessible name — never left to a colour/shape alone. */
private fun descrizioneSegno(segno: SegnoScheda): String = when (segno) {
    SegnoScheda.InAttesa -> "in coda"
    SegnoScheda.InCorso -> "in corso"
}

@Composable
private fun SegnoSchedaVista(segno: SegnoScheda, tinta: Color, modifier: Modifier) {
    when (segno) {
        // IconaSn takes no modifier of its own (AC-558): the tag lives on a wrapping Box.
        SegnoScheda.InAttesa -> Box(modifier) {
            IconaSn(Icona.Clock, descrizione = null, tinta = tinta, dimensione = SnastroMisure.iconS)
        }
        SegnoScheda.InCorso ->
            PallinoPulsante(
                colore = LocalSnastroColori.current.accent,
                diametro = DIAMETRO_PALLINO_SEGNO,
                modifier = modifier,
            )
    }
}
