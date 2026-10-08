package snastro.ui

import snastro.supporto.catturaNonFatale
import java.util.logging.Level
import java.util.logging.Logger

/** Under `snastro`, the logger `configuraLoggingApp` writes to the app's log file: a fault logged here is on disk. */
internal val logGuastiNonFatali: Logger = Logger.getLogger("snastro.ui.GuastoNonFatale")

/**
 * CR-7 at a presenter's edge: runs [blocco] through [catturaNonFatale]; a non-fatal fault is logged at WARNING with
 * its cause, naming [operazione], and answers null — the caller shows M1(b)'s generic message. Cancellation and fatal
 * errors still propagate. `inline`, so [blocco] may suspend.
 */
internal inline fun <T> nonFataleRegistrato(operazione: String, blocco: () -> T): T? =
    catturaNonFatale(blocco)
        .onFailure { logGuastiNonFatali.log(Level.WARNING, "$operazione: guasto non fatale", it) }
        .getOrNull()
