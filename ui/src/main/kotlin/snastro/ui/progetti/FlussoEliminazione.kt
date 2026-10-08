package snastro.ui.progetti

import snastro.kernel.Esito
import snastro.kernel.poi
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.SessioneProgetto
import snastro.ui.testi.avvisoEliminazione

/**
 * The delete-project flow of S1 · Progetti (R0): the confirmation ([ConfermaEliminazione]), the delete through
 * [SessioneProgetto.elimina] run by [corsa], the [elenco] reloaded without the project, and the message of what is
 * left on disk, its folder written `~` from [cartellaUtente].
 */
internal class FlussoEliminazione(
    private val corsa: CorsaSingola,
    private val elenco: ElencoProgetti,
    private val sessione: SessioneProgetto,
    private val cartellaUtente: String,
) {
    /** EX-1/EX-3: opens the delete confirmation of [progetto]. */
    fun chiedi(progetto: ProgettoVista) = corsa.aggiorna {
        if (it.inCorso) it else it.copy(conferma = ConfermaEliminazione(progetto.nome, progetto.percorso))
    }

    /** EX-3: closes the confirmation, unless its delete is already running; nothing is deleted. */
    fun annulla() = corsa.aggiorna { if (it.inCorso) it else it.copy(conferma = null) }

    /**
     * EX-1: deletes the confirmed project, then closes the confirmation over the list reloaded without it — only
     * when [nomeDigitato] is the project's exact name (EX-2: the view's disabled button is not the only guard) — and
     * says what is left on disk (EX-9, EX-10). A failure stays in the confirmation.
     */
    fun conferma(nomeDigitato: String) {
        val conferma = corsa.dati?.conferma ?: return
        if (!conferma.confermataDa(nomeDigitato)) return
        corsa.avvia(
            pulisci = { it.copy(conferma = conferma.copy(errore = null), avvisoEliminazione = null) },
            operazione = {
                sessione.elimina(conferma.percorso)
                    .poi { eliminato -> Esito.Ok(eliminato to elenco.progetti()) }
            },
            alSuccesso = { stato, (eliminato, progetti) ->
                stato.copy(
                    progetti = progetti,
                    conferma = null,
                    avvisoEliminazione = avvisoEliminazione(eliminato, cartellaUtente),
                )
            },
            alFallimento = { stato, messaggio -> stato.copy(conferma = stato.conferma?.copy(errore = messaggio)) },
        )
    }

    /** EX-9/EX-10: dismisses the message of what the last delete left on disk. */
    fun chiudiAvviso() = corsa.aggiorna { it.copy(avvisoEliminazione = null) }
}
