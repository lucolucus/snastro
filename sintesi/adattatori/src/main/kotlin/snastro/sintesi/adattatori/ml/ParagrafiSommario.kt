package snastro.sintesi.adattatori.ml

/**
 * Splits the model's Sommario into paragraphs (2026-09-30, user: "fai anche dei paragrafi"). Asking the model itself
 * for paragraphs broke the answer on Qwen3.5 9B (measured: the lists came back empty or the answer ended early), so
 * the adapter does it: at sentence ends, a paragraph closes at the first sentence end past [SOGLIA_PAROLE] words, and
 * a last tail shorter than [CODA_MINIMA] words joins the paragraph before. Paragraphs are separated by a blank line.
 * A text that already has blank lines is left as it is.
 */
internal object ParagrafiSommario {
    const val SOGLIA_PAROLE = 80
    const val CODA_MINIMA = 30

    /** A sentence end: `.`, `!`, `?` (with closing quotes/brackets) followed by whitespace. */
    private val FINE_FRASE = Regex("""(?<=[.!?][»"')\]]?)\s+""")
    private val SPAZI = Regex("""\s+""")

    fun dividi(testo: String): String {
        if ("\n\n" in testo) return testo
        val frasi = testo.trim().split(FINE_FRASE).filter { it.isNotBlank() }
        val paragrafi = mutableListOf<MutableList<String>>()
        var corrente = mutableListOf<String>()
        frasi.forEach { frase ->
            corrente += frase
            if (parole(corrente) >= SOGLIA_PAROLE) {
                paragrafi += corrente
                corrente = mutableListOf()
            }
        }
        when {
            corrente.isEmpty() -> Unit
            paragrafi.isNotEmpty() && parole(corrente) < CODA_MINIMA -> paragrafi.last() += corrente
            else -> paragrafi += corrente
        }
        return paragrafi.joinToString("\n\n") { it.joinToString(" ") }
    }

    private fun parole(frasi: List<String>): Int = frasi.sumOf { f -> f.split(SPAZI).count { it.isNotBlank() } }
}
