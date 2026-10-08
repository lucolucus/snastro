package snastro.ui

/**
 * What [SessioneProgetto.elimina] left on disk (R0). The project is out of the known projects in every case; only
 * what snastro created in its folder is deleted, so the folder may stay.
 */
sealed interface ProgettoEliminato {
    /** Nothing is left: the folder is gone too (EX-1). */
    data object Completamente : ProgettoEliminato

    /** Everything snastro created is gone; the folder at [cartella] stays with files the user put there (EX-9). */
    data class RestanoFileUtente(val cartella: String) : ProgettoEliminato

    /** Something snastro created could not be deleted: the folder at [cartella] stays (EX-10). */
    data class InParte(val cartella: String) : ProgettoEliminato
}
