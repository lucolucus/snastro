package snastro.ml

/** In-memory system properties that record every name read or written (AC-397: never java.library.path). */
internal class ProprietaSistemaFinte(vararg iniziali: Pair<String, String>) : ProprietaSistema {
    private val valori = mutableMapOf(*iniziali)
    val nomiToccati = mutableSetOf<String>()

    override fun leggi(nome: String): String? {
        nomiToccati += nome
        return valori[nome]
    }

    override fun imposta(nome: String, valore: String) {
        nomiToccati += nome
        valori[nome] = valore
    }
}
