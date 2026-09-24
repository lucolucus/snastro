package snastro.ui.registrazione

import snastro.parlanti.applicazione.letture.Candidato

/** The Proposta of a not-attributed card (AC-212..214, AC-405, AC-416). */
sealed interface StatoProposta {
    /** Being computed, still under `SOGLIA_ATTESA_VISIBILE_MS`: a loading indicator (AC-405). */
    data object Caricamento : StatoProposta

    /** AC-416: past the threshold — 'Proposta in attesa dell'elaborazione…', no 'Annulla'. */
    data object InAttesa : StatoProposta

    /** Ranked [candidati] (Fascia, never a number, AC-214); [nuovoEvidenziato] when all are `nessuna` (AC-213). */
    data class Pronta(val candidati: List<Candidato>, val nuovoEvidenziato: Boolean) : StatoProposta

    /** AC-405: this Voce's Proposta could not be read; 'altri ▾' / 'nuovo…' / 'salta' stay available. */
    data class Errore(val messaggio: String) : StatoProposta
}
