package snastro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import snastro.ui.stile.ColoriChiari
import snastro.ui.stile.ColoriScuri
import snastro.ui.stile.LocalRiduciMovimento
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroTipografiaDefault
import snastro.ui.stile.rilevaRiduciMovimentoSistema
import snastro.ui.stile.schemaMaterial
import snastro.ui.stile.tipografiaMaterial

/**
 * The one theme every screen wraps itself in (rule 11: what every screen shares). AC-552: follows
 * the macOS theme by default ([isSystemInDarkTheme]), provides [LocalSnastroColori] /
 * [LocalSnastroTipografia] (the Snastro design system, `snastro.ui.stile` — this block's body,
 * ADR 0001/0002), and maps them onto [MaterialTheme]'s `ColorScheme`/`Typography` (README
 * §'Implementazione in Compose') so Material components (`Icon`, `Surface`, …) stay legible too.
 * Call site unchanged for every existing screen: `scuro` defaults, so `SnastroTema { … }` still
 * compiles verbatim.
 *
 * Also turns OFF Material3's `minimumInteractiveComponentSize()` (the implicit 48dp touch-target
 * padding every clickable `Surface`/`Icon`Button gets by default): `Sn` components size themselves
 * exactly to `controlM`/`controlS` (AC-562/563/564) and their [snastro.ui.stile.anelloFocus] ring
 * must hug that real size, not a hidden 48dp box.
 *
 * [riduciMovimento] resolves the OS "reduce motion" signal once per composition ([remember]:
 * shelling out on every recomposition would be a perceptible stutter) unless the caller pins it —
 * tests/render-check always pin it (deterministic, and avoids ever creating a running
 * `rememberInfiniteTransition` inside a headless Compose UI test, AC-565).
 */
@Composable
fun SnastroTema(
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colori = if (scuro) ColoriScuri else ColoriChiari
    val riduciMovimentoRisolto = riduciMovimento ?: remember { rilevaRiduciMovimentoSistema() }
    CompositionLocalProvider(
        LocalSnastroColori provides colori,
        LocalSnastroTipografia provides SnastroTipografiaDefault,
        LocalRiduciMovimento provides riduciMovimentoRisolto,
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        MaterialTheme(
            colorScheme = schemaMaterial(colori, scuro),
            typography = tipografiaMaterial(SnastroTipografiaDefault),
            content = content,
        )
    }
}
