package snastro.ui.stile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * AC-558: renders [icona]'s SVG (`resources/icone/`) tinted with [tinta], sized [dimensione]
 * (default [SnastroMisure.iconM]) — the design system's only way to show an icon (never a text
 * glyph, AC-559). Uses `useResource`/`loadSvgPainter` (not the deprecated `painterResource(String)`)
 * — the plain classpath-resource loader, matching where AC-558 puts the SVGs.
 */
@Composable
public fun IconaSn(
    icona: Icona,
    descrizione: String?,
    tinta: Color = LocalContentColor.current,
    dimensione: Dp = SnastroMisure.iconM,
) {
    val densita = LocalDensity.current
    val painter = remember(icona, densita) { caricaIcona(icona, densita) }
    Icon(painter = painter, contentDescription = descrizione, tint = tinta, modifier = Modifier.size(dimensione))
}

// `useResource`/`loadSvgPainter` are deprecated in favor of the Compose Resources library (a
// `composeResources` source set + generated `Res` accessors) — a module-wide build.gradle.kts
// restructure this block's scope (`ui/src/main/resources/icone/`, as dispatched) does not ask for;
// suppressed locally rather than widening the change (frugality rung 6, CR-9 stays enforced elsewhere).
@Suppress("DEPRECATION")
private fun caricaIcona(icona: Icona, densita: Density): Painter =
    useResource("icone/${icona.file}") { flusso -> loadSvgPainter(flusso, densita) }
