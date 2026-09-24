package snastro.ui.stile

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val CIFRE_TABULARI = "tnum"
private const val TRACCIATO_DISPLAY = -0.01
private const val TRACCIATO_OVERLINE = 0.06

/**
 * AC-555: the 11 type styles of `UI/design-system/tokens.json`, exact size/line-height/weight/
 * letter-spacing. [overline] is UPPERCASE only when a future composable applies it — never typed
 * uppercase in the string it wraps.
 */
@Suppress("LongParameterList") // one property per design-system type style (AC-555), not a knob to trim
public data class SnastroTipografia(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val figure: TextStyle,
    val transcript: TextStyle,
    val abstract: TextStyle,
    val timecode: TextStyle,
)

public val SnastroTipografiaDefault: SnastroTipografia = SnastroTipografia(
    display = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = TRACCIATO_DISPLAY.em,
    ),
    title = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    heading = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    body = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    label = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    caption = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    overline = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = TRACCIATO_OVERLINE.em,
    ),
    figure = TextStyle(
        fontFamily = CarattereInterfaccia,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = CIFRE_TABULARI,
    ),
    transcript = TextStyle(
        fontFamily = CarattereLettura,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    abstract = TextStyle(
        fontFamily = CarattereLettura,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    timecode = TextStyle(
        fontFamily = CarattereDati,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = CIFRE_TABULARI,
    ),
)

/**
 * The active typography, provided by [snastro.ui.SnastroTema] (no light/dark variant — the type
 * scale is theme-agnostic).
 */
public val LocalSnastroTipografia: ProvidableCompositionLocal<SnastroTipografia> =
    staticCompositionLocalOf { SnastroTipografiaDefault }
