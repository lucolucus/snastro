package snastro.ui.registrazione

/**
 * The 'Riassegna per somiglianza' area of the Voci panel header (ADR 0019 §6 + Amendment (b).6), every
 * text and enabled flag decided by the presenter. [abilitato] is the button (AC-530); [suggerimento] its
 * disabled hint; [riferimenti] / [avvisoTuttaLaVoce] / [nonToccate] the lines under it; [fase] what is
 * running or shown now.
 */
data class PannelloSomiglianza(
    val abilitato: Boolean,
    val suggerimento: String?,
    val riferimenti: String?,
    val avvisoTuttaLaVoce: String?,
    val nonToccate: String?,
    val fase: FaseSomiglianza,
)

/** What the 'Riassegna per somiglianza' area shows (Amendment (b).2: compute → preview → apply). */
sealed interface FaseSomiglianza {
    data object Inattiva : FaseSomiglianza

    /** AC-531: 'Confronto le frasi… n di N' + a determinate bar; [inAttesa] past the threshold; 'Annulla'. */
    data class Calcolo(val testo: String, val fatti: Int, val totale: Int, val inAttesa: Boolean) : FaseSomiglianza

    /**
     * AC-545/AC-546: [titolo] + one line per group; 'Applica' / 'Annulla' when [applicabile] (N > 0), else
     * 'Chiudi' only; [inApplicazione] disables both buttons (no 'Annulla').
     */
    data class Anteprima(
        val titolo: String,
        val righe: List<String>,
        val applicabile: Boolean,
        val inApplicazione: Boolean,
    ) : FaseSomiglianza

    /** AC-533: the dismissible result line. */
    data class Esito(val testo: String) : FaseSomiglianza

    /** AC-533/AC-547: a dismissible error line; [ricalcola] offers 'Ricalcola' (TrascrittoCambiato). */
    data class Errore(val testo: String, val ricalcola: Boolean) : FaseSomiglianza
}
