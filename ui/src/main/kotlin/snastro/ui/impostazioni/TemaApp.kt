package snastro.ui.impostazioni

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** The app-wide theme choice of Impostazioni › Generali: follow macOS, or pin light/dark. */
enum class TemaApp { SISTEMA, CHIARO, SCURO }

/**
 * The theme choice in effect, provided once by the composition root over the whole window; every screen's
 * `scuro` default ([snastro.ui.temaScuro]) reads it. Unprovided (tests, render-check) it is [TemaApp.SISTEMA].
 */
val LocalTemaApp: ProvidableCompositionLocal<TemaApp> = staticCompositionLocalOf { TemaApp.SISTEMA }
