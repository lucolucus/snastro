package snastro.ui.progetti

/**
 * Fake [SceltaCartella] (RC-9): always answers [risultato] (`null` = the user cancelled), recording
 * every [titolo] a caller asked for so a test can assert WHICH dialog was opened (e.g. AC-573's
 * "Cambia cartella…" vs "Apri progetto…").
 */
class SceltaCartellaFinta(private val risultato: String? = null) : SceltaCartella {
    private val _titoliRichiesti = mutableListOf<String>()
    val titoliRichiesti: List<String> get() = _titoliRichiesti.toList()

    override fun scegli(titolo: String): String? {
        _titoliRichiesti += titolo
        return risultato
    }
}
