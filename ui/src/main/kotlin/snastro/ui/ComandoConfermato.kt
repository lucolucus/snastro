package snastro.ui

import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import java.util.logging.Level
import java.util.logging.Logger

@PublishedApi
internal val logComandoConfermato: Logger = Logger.getLogger("snastro.ui.ComandoConfermato")

/**
 * D-0062 (L259/L260): runs ONE command. A [ConsegnaDopoCommitFallita] means it COMMITTED and only an after-commit
 * subscriber then failed: the caller gets the committed outcome (`Ok`, so it reloads), the failure is only a
 * WARNING log — a committed change is never reported as failed. Any other failure propagates unchanged (an Error
 * after the commit too, D-0065).
 *
 * L276: the ONE copy of this rule — `inline`, so [comando] may suspend (a `withContext`, `:avvio`'s card commands
 * and similarity plan); public because `:avvio` (which already depends on `:ui`) uses it too.
 */
public inline fun comandoConfermato(cosa: String, comando: () -> Esito<Unit>): Esito<Unit> = try {
    comando()
} catch (e: ConsegnaDopoCommitFallita) {
    logComandoConfermato.log(Level.WARNING, "$cosa confermato, un abbonato dopo-commit e fallito", e)
    Esito.Ok(Unit)
}
