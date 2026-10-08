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
     * full-width banner with [AzioniProgetti.riprova], never conflated with `erroreCrea`. [conferma], when set, is
     * the open delete confirmation of one project.
     */
    data class Dati(
        val progetti: List<ProgettoVista>,
        val inCorso: Boolean = false,
        val erroreCrea: String? = null,
        val erroreApri: String? = null,
        val erroreElenco: String? = null,
        val conferma: ConfermaEliminazione? = null,
    ) : ProgettiUiStato
}

/**
 * The open delete confirmation of [progetto] (R0); [errore], when set, is the message of the delete that failed, shown
 * inside it like `erroreCrea` inside the new-project panel.
 */
data class ConfermaEliminazione(val progetto: ProgettoVista, val errore: String? = null) {
    /** EX-2: only the project's exact name confirms — same case, nothing more, nothing less. */
    fun confermataDa(nomeDigitato: String): Boolean = nomeDigitato == progetto.nome
}
