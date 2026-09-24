package snastro.ui.stile

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AC-565: whether motion should be suppressed (the OS "reduce motion" accessibility setting is on,
 * or the platform can't report it) — [ChipStato]'s running pulse reads this to fall back to a
 * constant dot. [snastro.ui.SnastroTema] resolves and provides the real value; outside a theme this
 * defaults to `true` (never animate by accident).
 */
public val LocalRiduciMovimento: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

private const val TIMEOUT_LETTURA_MS = 500L

/**
 * macOS's "Riduci il movimento" (`com.apple.universalaccess reduceMotion`), read ONCE per JVM on
 * first access — a blocking process start (bounded by [TIMEOUT_LETTURA_MS]), so it is only ever
 * forced through [riduciMovimentoSistema] (on [Dispatchers.IO]), never on the composition thread.
 */
private val riduciMovimentoSistemaPigro: Lazy<Boolean> = lazy { leggiRiduciMovimentoSistema() }

/** The already-resolved system value, or `null` if it has not been read yet (never blocks). */
public fun riduciMovimentoSistemaNoto(): Boolean? =
    if (riduciMovimentoSistemaPigro.isInitialized()) riduciMovimentoSistemaPigro.value else null

/** Resolves the system value on [Dispatchers.IO] the first time; later calls return the cached value. */
public suspend fun riduciMovimentoSistema(): Boolean =
    riduciMovimentoSistemaNoto() ?: withContext(Dispatchers.IO) { riduciMovimentoSistemaPigro.value }

private fun leggiRiduciMovimentoSistema(): Boolean {
    val sistemaOperativo = System.getProperty("os.name").orEmpty()
    if (!eMacOs(sistemaOperativo)) return interpretaRiduciMovimento(sistemaOperativo, null, "")
    @Suppress("SwallowedException") // deliberate platform-unavailable fallback (AC-565), see interpretaRiduciMovimento
    return try {
        val processo = ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (processo.waitFor(TIMEOUT_LETTURA_MS, TimeUnit.MILLISECONDS)) {
            val testo = processo.inputStream.bufferedReader().readText()
            interpretaRiduciMovimento(sistemaOperativo, processo.exitValue(), testo)
        } else {
            processo.destroyForcibly()
            interpretaRiduciMovimento(sistemaOperativo, null, "")
        }
    } catch (e: IOException) {
        interpretaRiduciMovimento(sistemaOperativo, null, "")
    }
}

private fun eMacOs(sistemaOperativo: String): Boolean = sistemaOperativo.lowercase().startsWith("mac")

/**
 * Pure decision half of the system read — unit-testable without shelling out.
 * - not macOS → `true` (the platform can't report it: the AC's constant-dot fallback);
 * - [codiceUscita] `null` (timeout / IOException) → `true` (same fallback);
 * - exit `0` → the printed value: `"1"` → `true`, `"0"` → `false`, anything unparseable → `true`;
 * - non-zero exit → `false`: on a default Mac the key is simply unset ("does not exist", exit 1),
 *   i.e. the user never turned reduce-motion on, so motion is allowed.
 */
internal fun interpretaRiduciMovimento(sistemaOperativo: String, codiceUscita: Int?, uscita: String): Boolean =
    when {
        !eMacOs(sistemaOperativo) -> true
        codiceUscita == null -> true
        codiceUscita != 0 -> false
        else -> uscita.trim() != "0"
    }
