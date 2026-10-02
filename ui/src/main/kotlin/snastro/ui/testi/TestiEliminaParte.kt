package snastro.ui.testi

/** ADR 0038 §5: the title and the text (with a Trascritto) of the confirmation for a Parte that is not the last of
 * its Incontro; the second paragraph ([MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO]) and the text without a
 * Trascritto are unchanged. */
fun titoloConfermaEliminaParte(numero: Int, titoloIncontro: String): String =
    "Eliminare la parte $numero di «$titoloIncontro»?"
const val MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO: String =
    "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, " +
        "le voci che compaiono solo in questa parte, la sbobinatura e le impronte vocali ricavate da questa parte. " +
        "Il riassunto dell'incontro resta leggibile ma diventa superato. Non si può annullare."
