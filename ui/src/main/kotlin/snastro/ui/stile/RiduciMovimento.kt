package snastro.ui.stile

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.IOException

/**
 * AC-565: whether motion should be suppressed (the OS "reduce motion" accessibility setting is on,
 * or the platform can't report it) — [ChipStato]'s running pulse reads this to fall back to a
 * constant dot. [snastro.ui.SnastroTema] resolves and provides the real value; outside a theme this
 * defaults to `true` (never animate by accident).
 */
public val LocalRiduciMovimento: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

/**
 * Reads macOS's "Riduci il movimento" (`com.apple.universalaccess.reduceMotion`) once. Only an
 * explicit `"0"` means motion is allowed; a non-zero exit, an unparseable value, or the command not
 * existing at all (any other platform) all degrade to `true` — the AC's own fallback ("if
 * unavailable on the platform, a constant dot").
 */
public fun rilevaRiduciMovimentoSistema(): Boolean {
    @Suppress("SwallowedException") // deliberate platform-unavailable fallback, documented above and on the AC
    val esito = try {
        val processo = ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion").start()
        val testo = processo.inputStream.bufferedReader().readText()
        val codiceUscita = processo.waitFor()
        (codiceUscita == 0) to testo
    } catch (e: IOException) {
        false to ""
    }
    return interpretaRiduciMovimento(esito.first, esito.second)
}

/** Pure parsing half of [rilevaRiduciMovimentoSistema] — unit-testable without shelling out. */
internal fun interpretaRiduciMovimento(comandoRiuscito: Boolean, valoreGrezzo: String): Boolean =
    !comandoRiuscito || valoreGrezzo.trim() != "0"
