package snastro.ui

/**
 * Fake [ApriEsterno] (RC-9): records every call so a test can assert WHICH `percorso` was opened/shown
 * (AC-218 — 'Apri documento'/'Mostra nella cartella' use the Documento's path), without touching the OS.
 */
class ApriEsternoFinta : ApriEsterno {
    private val _fileAperti = mutableListOf<String>()
    val fileAperti: List<String> get() = _fileAperti.toList()

    private val _cartelleMostrate = mutableListOf<String>()
    val cartelleMostrate: List<String> get() = _cartelleMostrate.toList()

    override fun apriFile(percorso: String) {
        _fileAperti += percorso
    }

    override fun mostraNellaCartella(percorso: String) {
        _cartelleMostrate += percorso
    }
}
