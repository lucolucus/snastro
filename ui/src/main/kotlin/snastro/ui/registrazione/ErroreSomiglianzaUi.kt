package snastro.ui.registrazione

/** Why "Riassegna per somiglianza" ended without a result, in UI terms (CR-1: no `dominio` type in the port). */
sealed interface ErroreSomiglianzaUi {
    /** The transcript changed after the preview: nothing written, the plan discarded ('Ricalcola'). */
    data object TrascrittoCambiato : ErroreSomiglianzaUi

    /** Fewer than two reference persons (INV-27). */
    data object RiferimentiInsufficienti : ErroreSomiglianzaUi

    /** Any other failure, already in plain words (AC-404 rule). */
    data class Altro(val testo: String) : ErroreSomiglianzaUi
}
