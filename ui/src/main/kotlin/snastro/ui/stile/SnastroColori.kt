package snastro.ui.stile

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AC-551: one field per colour token of `UI/design-system/tokens.json`. [ColoriChiari] / [ColoriScuri]
 * carry the exact hex values of the `light` / `dark` themes — `SnastroColoriTest` parses the token
 * file itself (copied to `src/test/resources/design-system/tokens.json`) so this class can never
 * drift from the design system.
 */
@Suppress("LongParameterList") // one property per design-system colour token (AC-551), not a knob to trim
public data class SnastroColori(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val sunken: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val focus: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val voci: List<Color>,
)

private const val RADICE_ESADECIMALE = 16

private fun esadecimale(hex: String): Color = Color(("FF" + hex.removePrefix("#")).toLong(RADICE_ESADECIMALE))

public val ColoriChiari: SnastroColori = SnastroColori(
    ground = esadecimale("#f4f2ee"),
    surface = esadecimale("#fbfaf7"),
    raised = esadecimale("#ffffff"),
    sunken = esadecimale("#ebe8e2"),
    line = esadecimale("#e0dcd4"),
    lineStrong = esadecimale("#8f8a80"),
    ink = esadecimale("#221e1a"),
    inkMuted = esadecimale("#605a52"),
    inkFaint = esadecimale("#928c83"),
    accent = esadecimale("#dd8f5e"),
    onAccent = esadecimale("#2a1206"),
    accentHover = esadecimale("#e8a67c"),
    accentSoft = esadecimale("#f5e8de"),
    accentInk = esadecimale("#98522c"),
    focus = esadecimale("#98522c"),
    danger = esadecimale("#b0243a"),
    dangerSoft = esadecimale("#fbe3e6"),
    warning = esadecimale("#2f5a8a"),
    warningSoft = esadecimale("#e3ecf6"),
    voci = listOf(
        esadecimale("#2463a8"),
        esadecimale("#5f7d24"),
        esadecimale("#16876a"),
        esadecimale("#b0407a"),
        esadecimale("#6649b0"),
        esadecimale("#0b8597"),
        esadecimale("#8a6c16"),
        esadecimale("#5a6670"),
    ),
)

public val ColoriScuri: SnastroColori = SnastroColori(
    ground = esadecimale("#15130f"),
    surface = esadecimale("#1c1a16"),
    raised = esadecimale("#25221d"),
    sunken = esadecimale("#0f0d0a"),
    line = esadecimale("#332f29"),
    lineStrong = esadecimale("#756e63"),
    ink = esadecimale("#eeeae3"),
    inkMuted = esadecimale("#aaa399"),
    inkFaint = esadecimale("#7a746b"),
    accent = esadecimale("#d9956a"),
    onAccent = esadecimale("#2a1206"),
    accentHover = esadecimale("#e6ae88"),
    accentSoft = esadecimale("#33261c"),
    accentInk = esadecimale("#e2a881"),
    focus = esadecimale("#e6ae88"),
    danger = esadecimale("#ff8a9a"),
    dangerSoft = esadecimale("#3a1519"),
    warning = esadecimale("#8fb6e6"),
    warningSoft = esadecimale("#172433"),
    voci = listOf(
        esadecimale("#72acec"),
        esadecimale("#a9c663"),
        esadecimale("#4ec79f"),
        esadecimale("#e883b6"),
        esadecimale("#ab96f2"),
        esadecimale("#52c5d4"),
        esadecimale("#d4b45c"),
        esadecimale("#9ba8b1"),
    ),
)

/** AC-552: the active theme's colours, provided by [snastro.ui.SnastroTema]. */
public val LocalSnastroColori: ProvidableCompositionLocal<SnastroColori> = staticCompositionLocalOf { ColoriChiari }
