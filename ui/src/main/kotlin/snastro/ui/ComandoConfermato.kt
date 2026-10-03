package snastro.ui

import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import java.util.logging.Level
import java.util.logging.Logger

private val log: Logger = Logger.getLogger("snastro.ui.ComandoConfermato")

/**
 * D-0062 (L259/L260): runs ONE command. A [ConsegnaDopoCommitFallita] means it COMMITTED and only an after-commit
 * subscriber then failed: the screen shows the committed outcome (`Ok`, so it reloads), the failure is only a
 * WARNING log — a committed change is never reported as failed. Any other failure propagates unchanged.
 */
internal fun comandoConfermato(cosa: String, comando: () -> Esito<Unit>): Esito<Unit> = try {
    comando()
} catch (e: ConsegnaDopoCommitFallita) {
    log.log(Level.WARNING, "$cosa confermato, un abbonato dopo-commit e fallito", e)
    Esito.Ok(Unit)
}
