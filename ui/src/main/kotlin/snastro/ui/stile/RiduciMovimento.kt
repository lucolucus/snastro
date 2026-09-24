package snastro.ui.stile

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * macOS's "Riduci il movimento" (`com.apple.universalaccess reduceMotion`), read on first access —
 * a blocking process start (bounded by [TIMEOUT_LETTURA_MS]), so it is only ever forced through
 * [riduciMovimentoSistema] (on [Dispatchers.IO]), never on the composition thread. L723: a
 * DEFINITIVE read (the process actually completed, exit code and all) is cached for the rest of the
 * JVM; a timeout or a failure to even start the process is NOT cached — a transient hiccup must not
 * pin "reduce motion" forever, so the next caller gets to try again.
 */
@Volatile
private var cacheRiduciMovimentoSistema: Boolean? = null

/** The already-resolved system value, or `null` if it has not been (definitively) read yet. */
public fun riduciMovimentoSistemaNoto(): Boolean? = cacheRiduciMovimentoSistema

/** Resolves the system value on [Dispatchers.IO]; a definitive read is cached, a timeout/failure is not. */
public suspend fun riduciMovimentoSistema(): Boolean =
    cacheRiduciMovimentoSistema ?: withContext(Dispatchers.IO) {
        val esito = leggiRiduciMovimentoSistema()
        if (esito.definitivo) cacheRiduciMovimentoSistema = esito.valore
        esito.valore
    }

private class RisultatoLetturaMovimento(val valore: Boolean, val definitivo: Boolean)

private fun leggiRiduciMovimentoSistema(): RisultatoLetturaMovimento {
    val sistemaOperativo = System.getProperty("os.name").orEmpty()
    if (!eMacOs(sistemaOperativo)) {
        return RisultatoLetturaMovimento(interpretaRiduciMovimento(sistemaOperativo, null, ""), definitivo = true)
    }
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // deliberate platform-unavailable
    // fallback (AC-565): ANY failure to run `defaults` (not just IOException — L723 widens the net,
    // e.g. a SecurityException from a locked-down sandbox) falls back to the constant-dot default
    // rather than crashing the theme, see interpretaRiduciMovimento.
    return try {
        val processo = ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (processo.waitFor(TIMEOUT_LETTURA_MS, TimeUnit.MILLISECONDS)) {
            // L723: the stream was never closed — `use {}` releases the underlying fd once read.
            val testo = processo.inputStream.bufferedReader().use { it.readText() }
            val valore = interpretaRiduciMovimento(sistemaOperativo, processo.exitValue(), testo)
            RisultatoLetturaMovimento(valore, definitivo = true)
        } else {
            processo.destroyForcibly()
            RisultatoLetturaMovimento(true, definitivo = false) // L723: timeout — retry next time
        }
    } catch (e: Exception) {
        RisultatoLetturaMovimento(true, definitivo = false) // L723: never cache a failed attempt
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
