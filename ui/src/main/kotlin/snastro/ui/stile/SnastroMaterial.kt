package snastro.ui.stile

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * AC-552: `ColorScheme` mapped from [SnastroColori] per README §'Implementazione in Compose' —
 * `primary` = accent, `onPrimary` = onAccent, `background` = ground, `surface` = surface,
 * `surfaceContainer` = raised, `surfaceVariant` = sunken, `outline` = lineStrong,
 * `outlineVariant` = line, `error` = danger, `onSurface` = ink, `onSurfaceVariant` = inkMuted.
 */
public fun schemaMaterial(colori: SnastroColori, scuro: Boolean): ColorScheme = if (scuro) {
    darkColorScheme(
        primary = colori.accent,
        onPrimary = colori.onAccent,
        background = colori.ground,
        surface = colori.surface,
        surfaceContainer = colori.raised,
        surfaceVariant = colori.sunken,
        outline = colori.lineStrong,
        outlineVariant = colori.line,
        error = colori.danger,
        onSurface = colori.ink,
        onSurfaceVariant = colori.inkMuted,
    )
} else {
    lightColorScheme(
        primary = colori.accent,
        onPrimary = colori.onAccent,
        background = colori.ground,
        surface = colori.surface,
        surfaceContainer = colori.raised,
        surfaceVariant = colori.sunken,
        outline = colori.lineStrong,
        outlineVariant = colori.line,
        error = colori.danger,
        onSurface = colori.ink,
        onSurfaceVariant = colori.inkMuted,
    )
}

/**
 * AC-555: Material `Typography` maps bodyMedium = body, labelLarge = label, titleMedium = title,
 * headlineSmall = display, bodySmall = caption.
 */
public fun tipografiaMaterial(tipografia: SnastroTipografia = SnastroTipografiaDefault): Typography = Typography(
    bodyMedium = tipografia.body,
    labelLarge = tipografia.label,
    titleMedium = tipografia.title,
    headlineSmall = tipografia.display,
    bodySmall = tipografia.caption,
)
