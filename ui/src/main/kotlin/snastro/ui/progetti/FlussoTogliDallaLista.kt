package snastro.ui.progetti

import snastro.kernel.Esito
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.SessioneProgetto

/**
 * The "Togli dalla lista" flow of S1 · Progetti (R0, EX-8): a project whose folder is not found is dropped from the
 * known projects through [SessioneProgetto.togliDallaLista] — nothing is deleted — run by [corsa], then the [elenco]
 * is reloaded without it. A failure is a dismissible message over the list.
 */
internal class FlussoTogliDallaLista(
    private val corsa: CorsaSingola,
    private val elenco: ElencoProgetti,
    private val sessione: SessioneProgetto,
) {
    /** EX-8: drops [progetto] from the list. */
    fun togli(progetto: ProgettoVista) = corsa.avvia(
        pulisci = { it.copy(erroreTogli = null) },
        operazione = {
            sessione.togliDallaLista(progetto.percorso)
            Esito.Ok(elenco.progetti())
        },
        alSuccesso = { stato, progetti -> stato.copy(progetti = progetti) },
        alFallimento = { stato, messaggio -> stato.copy(erroreTogli = messaggio) },
    )

    /** Dismisses the message of the "Togli dalla lista" that failed. */
    fun chiudiErrore() = corsa.aggiorna { it.copy(erroreTogli = null) }
}
