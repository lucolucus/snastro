package snastro.ui.progetti

import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista

/** State of S1 · Progetti (AC-192..198) — presenter-owned, rendered by [SchermataProgetti]. */
sealed interface ProgettiUiStato {
    /** [ElencoProgetti] is still loading (AC-193). */
    data object Caricamento : ProgettiUiStato

    /**
     * The known projects (AC-198), in [ElencoProgetti]'s own order (most recent activity first) — an
     * empty [progetti] is rendered as the AC-192 empty message, not a separate state. [inCorso] is
     * true while a `crea`/`apri` is in flight (double-submit guard, M3). [erroreCrea]/[erroreApri],
     * when set, are dismissible inline messages for the last failed `crea`/`apri` (H1: an error never
     * replaces [progetti] — [AzioniProgetti.chiudiErroreCrea]/[AzioniProgetti.chiudiErroreApri] clear
     * them). [erroreElenco] (L530d), when set, is the INITIAL elenco load's own failure — a distinct
     * full-width banner with [AzioniProgetti.riprova], never conflated with `erroreCrea`.
     */
    data class Dati(
        val progetti: List<ProgettoVista>,
        val inCorso: Boolean = false,
        val erroreCrea: String? = null,
        val erroreApri: String? = null,
        val erroreElenco: String? = null,
    ) : ProgettiUiStato
}
