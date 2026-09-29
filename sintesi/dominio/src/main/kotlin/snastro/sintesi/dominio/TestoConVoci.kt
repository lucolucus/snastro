package snastro.sintesi.dominio

import snastro.kernel.VoceId

/**
 * A text whose speakers are Voce references (INV-S5), with the lossless storage/port codec (ADR 0022):
 * a speaker is `{V<n>}`, a literal brace is doubled (`{{`, `}}`).
 */
public data class TestoConVoci(val parti: List<ParteTesto>) {
    init {
        // Programmer-error guard (CR-5): keeps every [ParteTesto.Voce] inside the [TOKEN] range so `codifica()`
        // can never write what `decodifica()` would reject (A28: n >= 1, at most 9 digits, no leading zero).
        parti.filterIsInstance<ParteTesto.Voce>().forEach { voce ->
            require(voce.voceId.numero in 1..VOCE_ID_MASSIMO) { "VoceId non canonico: ${voce.voceId.numero}" }
        }
    }

    /** Every Voce referenced in the text. */
    public val voci: Set<VoceId> get() = parti.filterIsInstance<ParteTesto.Voce>().map { it.voceId }.toSet()

    public fun codifica(): String = buildString {
        parti.forEach { parte ->
            when (parte) {
                is ParteTesto.Testo -> append(parte.testo.replace("{", "{{").replace("}", "}}"))
                is ParteTesto.Voce -> append("{V").append(parte.voceId.numero).append('}')
            }
        }
    }

    public companion object {
        /** Parses the `{V<n>}` form; `null` on a malformed token (a lone brace, `{V}`, `{V0}`, `{Vx}`, `{V01}`). */
        public fun decodifica(s: String): TestoConVoci? {
            val simboli = TOKEN.findAll(s).toList()
            if (simboli.any { it.value == "{" || it.value == "}" }) return null
            val parti = mutableListOf<ParteTesto>()
            val testo = StringBuilder()
            var da = 0
            simboli.forEach { m ->
                testo.append(s, da, m.range.first)
                da = m.range.last + 1
                val numero = m.groupValues[1]
                if (numero.isEmpty()) {
                    testo.append(m.value[0]) // a doubled literal brace
                } else {
                    if (testo.isNotEmpty()) parti += ParteTesto.Testo(testo.toString())
                    testo.clear()
                    parti += ParteTesto.Voce(VoceId(numero.toInt()))
                }
            }
            testo.append(s, da, s.length)
            if (testo.isNotEmpty()) parti += ParteTesto.Testo(testo.toString())
            return TestoConVoci(parti)
        }

        /** `{{`, `}}`, a well-formed `{V<n>}` (n ≥ 1, no leading zero: lossless), or a lone brace (malformed). */
        private val TOKEN = Regex("""\{\{|}}|\{V([1-9][0-9]{0,8})}|[{}]""")

        /** The [TOKEN] regex's upper bound: at most 9 digits, no leading zero (A28). */
        private const val VOCE_ID_MASSIMO: Int = 999_999_999
    }
}
