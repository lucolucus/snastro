package snastro.ui

import snastro.supporto.catturaEccezione
import snastro.supporto.catturaNonFatale
import java.util.logging.Level
import java.util.logging.Logger

/** Under `snastro`, the logger `configuraLoggingApp` writes to the app's log file: a fault logged here is on disk. */
private val logGuastiNonFatali: Logger = Logger.getLogger("snastro.ui.GuastoNonFatale")

/**
 * CR-7 at a presenter's edge: runs [blocco] through [catturaNonFatale]; a non-fatal fault is logged at WARNING with
 * its cause, naming [operazione], and answers null — the caller shows M1(b)'s generic message. Cancellation and fatal
 * errors still propagate. `inline`, so [blocco] may suspend. A committing command uses [eccezioneRegistrata] instead
 * (ADR 0041 §3).
 */
internal inline fun <T> nonFataleRegistrato(operazione: String, blocco: () -> T): T? =
    catturaNonFatale(blocco).registrato(operazione)

/**
 * CR-7 at the edge of a committing command (`comandoConfermato`, an import): runs [blocco] through
 * [catturaEccezione]; an `Exception` is logged like [nonFataleRegistrato] and answers null, while cancellation and
 * every `Error` propagate (D-0065: an `Error` after the commit is fatal). `inline`, so [blocco] may suspend.
 */
internal inline fun <T> eccezioneRegistrata(operazione: String, blocco: () -> T): T? =
    catturaEccezione(blocco).registrato(operazione)

/** The one WARNING line of both helpers above: [operazione] and the cause; null on failure. */
private fun <T> Result<T>.registrato(operazione: String): T? =
    onFailure { logGuastiNonFatali.log(Level.WARNING, "$operazione: guasto non fatale", it) }.getOrNull()
