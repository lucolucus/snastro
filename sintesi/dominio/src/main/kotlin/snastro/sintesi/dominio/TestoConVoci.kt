package snastro.sintesi.dominio

import snastro.kernel.Esito
import snastro.kernel.VoceId

/**
 * A text whose speakers are Voce references (INV-S5), with the lossless storage/port codec (ADR 0022):
 * a speaker is `{V<n>}`, a literal brace is doubled (`{{`, `}}`).
 */
public data class TestoConVoci(val parti: List<ParteTesto>) {
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
        /** Parses the `{V<n>}` form; a malformed token → [ErroreSintesi.TokenVoceMalformato]. */
        public fun decodifica(s: String): Esito<TestoConVoci> {
            val simboli = TOKEN.findAll(s).toList()
            simboli.firstOrNull { it.value == "{" || it.value == "}" }
                ?.let { return Esito.Errore(ErroreSintesi.TokenVoceMalformato(it.range.first)) }
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
            return Esito.Ok(TestoConVoci(parti))
        }

        /** `{{`, `}}`, a well-formed `{V<n>}` (n ≥ 1, no leading zero: lossless), or a lone brace (malformed). */
        private val TOKEN = Regex("""\{\{|}}|\{V([1-9][0-9]{0,8})}|[{}]""")
    }
}
