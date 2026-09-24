package snastro.ui

import kotlinx.coroutines.flow.StateFlow
import snastro.kernel.Esito

/**
 * `tec-shell-ui` (owned here, in-process, consumer-driven contract test — [SessioneProgettoContratto]):
 * the UI-side session over one open [ProgettoAperto], implemented by `:avvio` (folder layout, `.lock`,
 * database lifecycle — ADR 0010). `crea`/`apri` are blocking (dev-architecture `#servizio`): the
 * presenter dispatches them on its own IO dispatcher.
 */
interface SessioneProgetto {
    /** The Progetto currently open, or `null` if none is (one project open per app instance). */
    val corrente: StateFlow<ProgettoAperto?>

    /** Creates a new project folder under [cartellaGenitore] named after [nome] and opens it. */
    fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>

    /** Opens the project folder at [percorso]. */
    fun apri(percorso: String): Esito<ProgettoAperto>

    /**
     * Closes the currently open project, if any ([corrente] becomes `null`). Blocking too — it may wait
     * (bounded) for the project's background work to stop — so the presenter runs it on its IO dispatcher.
     */
    fun chiudi()
}
