package snastro.ui.stile

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private const val ALPHA_SCRIM = 0.32f

/**
 * AC-552: `ColorScheme` mapped from [SnastroColori] per README §'Implementazione in Compose' —
 * `primary` = accent, `onPrimary` = onAccent, `background` = ground, `surface` = surface,
 * `surfaceContainer` = raised, `surfaceVariant` = sunken, `outline` = lineStrong,
 * `outlineVariant` = line, `error` = danger, `onSurface` = ink, `onSurfaceVariant` = inkMuted.
 *
 * Review MED-5: every OTHER slot is mapped too — Material has no "unset" for a `ColorScheme` field,
 * so anything left untouched falls back to the default purple M3 palette. `surfaceTint` is
 * transparent (this design system has no tonal-elevation tint, `CardSn`/`BannerSn` etc. paint their
 * own flat fills) and `scrim` is a plain black at 32% (Material's own convention, no design-system
 * token names one).
 */
// An exhaustive ColorScheme mapping (review MED-5) is inherently a long flat list, not a complexity smell.
@Suppress("LongMethod")
public fun schemaMaterial(colori: SnastroColori, scuro: Boolean): ColorScheme = if (scuro) {
    darkColorScheme(
        primary = colori.accent,
        onPrimary = colori.onAccent,
        primaryContainer = colori.accentSoft,
        onPrimaryContainer = colori.ink,
        secondary = colori.accentInk,
        onSecondary = colori.raised,
        secondaryContainer = colori.accentSoft,
        onSecondaryContainer = colori.ink,
        tertiary = colori.accentInk,
        background = colori.ground,
        onBackground = colori.ink,
        surface = colori.surface,
        onSurface = colori.ink,
        surfaceVariant = colori.sunken,
        onSurfaceVariant = colori.inkMuted,
        surfaceContainerLowest = colori.surface,
        surfaceContainerLow = colori.surface,
        surfaceContainer = colori.raised,
        surfaceContainerHigh = colori.raised,
        surfaceContainerHighest = colori.raised,
        surfaceTint = Color.Transparent,
        outline = colori.lineStrong,
        outlineVariant = colori.line,
        error = colori.danger,
        errorContainer = colori.dangerSoft,
        onErrorContainer = colori.danger,
        inverseSurface = colori.ink,
        inverseOnSurface = colori.surface,
        scrim = Color.Black.copy(alpha = ALPHA_SCRIM),
    )
} else {
    lightColorScheme(
        primary = colori.accent,
        onPrimary = colori.onAccent,
        primaryContainer = colori.accentSoft,
        onPrimaryContainer = colori.ink,
        secondary = colori.accentInk,
        onSecondary = colori.raised,
        secondaryContainer = colori.accentSoft,
        onSecondaryContainer = colori.ink,
        tertiary = colori.accentInk,
        background = colori.ground,
        onBackground = colori.ink,
        surface = colori.surface,
        onSurface = colori.ink,
        surfaceVariant = colori.sunken,
        onSurfaceVariant = colori.inkMuted,
        surfaceContainerLowest = colori.surface,
        surfaceContainerLow = colori.surface,
        surfaceContainer = colori.raised,
        surfaceContainerHigh = colori.raised,
        surfaceContainerHighest = colori.raised,
        surfaceTint = Color.Transparent,
        outline = colori.lineStrong,
        outlineVariant = colori.line,
        error = colori.danger,
        errorContainer = colori.dangerSoft,
        onErrorContainer = colori.danger,
        inverseSurface = colori.ink,
        inverseOnSurface = colori.surface,
        scrim = Color.Black.copy(alpha = ALPHA_SCRIM),
    )
}

/**
 * AC-555: Material `Typography` maps bodyMedium = body, labelLarge = label, titleMedium = title,
 * headlineSmall = display, bodySmall = caption. Review MED-6: the remaining sizes Material
 * components reach for by default (tooltips, any unstyled `Text`) are mapped too, all in the `ui`
 * family (Instrument Sans) — bodyLarge = body, labelMedium = label, labelSmall = `caption` at
 * `Medium` weight, titleSmall = heading, titleLarge = title.
 */
public fun tipografiaMaterial(tipografia: SnastroTipografia = SnastroTipografiaDefault): Typography = Typography(
    bodyLarge = tipografia.body,
    bodyMedium = tipografia.body,
    bodySmall = tipografia.caption,
    labelLarge = tipografia.label,
    labelMedium = tipografia.label,
    labelSmall = tipografia.caption.copy(fontWeight = FontWeight.Medium),
    titleSmall = tipografia.heading,
    titleMedium = tipografia.title,
    titleLarge = tipografia.title,
    headlineSmall = tipografia.display,
)
