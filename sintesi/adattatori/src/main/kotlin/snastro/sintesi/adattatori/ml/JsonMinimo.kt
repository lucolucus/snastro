package snastro.sintesi.adattatori.ml

/**
 * A minimal strict JSON reader (RFC 8259 values: object → `Map`, array → `List`, string, integer → `Long`, other
 * number → `Double`, `true`/`false`, `null`) for the model's answer — the project has no JSON library and the
 * answer is one small document. A failure on any syntax error or trailing content.
 */
internal class JsonMinimo private constructor(private val s: String) {
    private var i = 0

    private class NonValido : RuntimeException()

    private fun valore(): Any? {
        spazi()
        return when (s.getOrNull(i)) {
            '{' -> oggetto()
            '[' -> lista()
            '"' -> stringa()
            't' -> parola("true", true)
            'f' -> parola("false", false)
            'n' -> parola("null", null)
            else -> numero()
        }
    }

    private fun oggetto(): Map<String, Any?> {
        i++
        val mappa = LinkedHashMap<String, Any?>()
        spazi()
        if (prendi('}')) return mappa
        do {
            spazi()
            richiedi(s.getOrNull(i) == '"')
            val chiave = stringa()
            spazi()
            richiedi(prendi(':') && !mappa.containsKey(chiave))
            mappa[chiave] = valore()
            spazi()
        } while (prendi(','))
        richiedi(prendi('}'))
        return mappa
    }

    private fun lista(): List<Any?> {
        i++
        val elementi = mutableListOf<Any?>()
        spazi()
        if (prendi(']')) return elementi
        do {
            elementi += valore()
            spazi()
        } while (prendi(','))
        richiedi(prendi(']'))
        return elementi
    }

    private fun stringa(): String {
        i++
        val sb = StringBuilder()
        while (true) {
            val c = s.getOrNull(i++) ?: throw NonValido()
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> sb.append(escape())
                c < ' ' -> throw NonValido()
                else -> sb.append(c)
            }
        }
    }

    private fun escape(): Char = when (s.getOrNull(i++)) {
        '"' -> '"'
        '\\' -> '\\'
        '/' -> '/'
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> s.substring(i, minOf(i + CIFRE_UNICODE, s.length)).also { i += CIFRE_UNICODE }
            .takeIf { ESADECIMALE.matches(it) }?.toInt(BASE_ESADECIMALE)?.toChar() ?: throw NonValido()
        else -> throw NonValido()
    }

    private fun numero(): Any {
        val m = NUMERO.matchAt(s, i) ?: throw NonValido()
        i = m.range.last + 1
        return m.value.toLongOrNull() ?: m.value.toDouble()
    }

    private fun parola(testo: String, valore: Any?): Any? {
        richiedi(s.startsWith(testo, i))
        i += testo.length
        return valore
    }

    private fun prendi(c: Char): Boolean = (s.getOrNull(i) == c).also { if (it) i++ }

    private fun richiedi(condizione: Boolean) {
        if (!condizione) throw NonValido()
    }

    private fun spazi() {
        while (s.getOrNull(i)?.let { it in SPAZI } == true) i++
    }

    companion object {
        private const val CIFRE_UNICODE = 4
        private const val BASE_ESADECIMALE = 16
        private val ESADECIMALE = Regex("[0-9a-fA-F]{4}")
        private const val SPAZI = " \n\r\t"
        private val NUMERO = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")

        /** The value of the JSON document [testo]; a failure when [testo] is not exactly one JSON value. */
        fun leggi(testo: String): Result<Any?> {
            val lettore = JsonMinimo(testo)
            return try {
                val valore = lettore.valore()
                lettore.spazi()
                lettore.richiedi(lettore.i == testo.length)
                Result.success(valore)
            } catch (e: NonValido) {
                Result.failure(IllegalArgumentException("JSON non valido alla posizione ${lettore.i}", e))
            } catch (e: NumberFormatException) {
                Result.failure(IllegalArgumentException("numero JSON non valido", e))
            }
        }
    }
}
