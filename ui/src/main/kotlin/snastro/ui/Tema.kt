package snastro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import snastro.ui.stile.ColoriChiari
import snastro.ui.stile.ColoriScuri
import snastro.ui.stile.LocalRiduciMovimento
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroTipografiaDefault
import snastro.ui.stile.riduciMovimentoSistema
import snastro.ui.stile.riduciMovimentoSistemaNoto
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
 * [riduciMovimento], unless the caller pins it, is the OS "reduce motion" signal: read ONCE per JVM
 * off the UI thread ([riduciMovimentoSistema] on `Dispatchers.IO`, cached); until that read lands the
 * theme provides `true` (a still dot, never an accidental animation — AC-565), later screens get the
 * cached value immediately. Tests/render-check always pin it (deterministic, no process started, and
 * no running `rememberInfiniteTransition` inside a headless Compose UI test).
 */
@Composable
fun SnastroTema(
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colori = if (scuro) ColoriScuri else ColoriChiari
    val riduciMovimentoRisolto = riduciMovimento
        ?: produceState(initialValue = riduciMovimentoSistemaNoto() ?: true) { value = riduciMovimentoSistema() }.value
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
