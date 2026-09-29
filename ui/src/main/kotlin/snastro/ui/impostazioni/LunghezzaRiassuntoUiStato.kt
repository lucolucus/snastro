package snastro.ui.impostazioni

/** State of Impostazioni › Riassunto for the open project: the lunghezza massima del Riassunto editor. */
sealed interface LunghezzaRiassuntoUiStato {
    data object Caricamento : LunghezzaRiassuntoUiStato

    /**
     * [testo] is the field's content, [salvata] the value last read/saved; [errore] the inline message (out of
     * range, or the command's own error); [conferma] is shown right after a successful save.
     */
    data class Dati(
        val testo: String,
        val salvata: Int,
        val minimo: Int,
        val massimo: Int,
        val inCorso: Boolean = false,
        val errore: String? = null,
        val conferma: Boolean = false,
    ) : LunghezzaRiassuntoUiStato {
        val modificata: Boolean get() = testo.trim() != salvata.toString()
    }

    /** The setting could not be read. */
    data class Errore(val messaggio: String) : LunghezzaRiassuntoUiStato
}
