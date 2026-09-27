package snastro.sintesi.adattatori.ml

/**
 * Re-expresses a text written by the model in the port's canonical form (ADR 0021 §4, the `TestoConVoci` codec):
 * every speaker reference the prompt's syntax admits — `{V<n>}` (the one asked for), `{ V<n> }`, `[V<n>]`, a bare
 * `V<n>`, `Voce <n>` — becomes `{V<n>}`; every other brace is literal and is doubled. A number the codec cannot
 * carry (`0`, more than 9 digits) stays plain text.
 */
internal object VociNelTesto {
    private val RIFERIMENTO = Regex("""\{\s*V(\d+)\s*}|\[V(\d+)]|\bVoce\s+(\d+)\b|\bV(\d+)\b""")
    private const val MASSIMO_VOCE = 999_999_999

    fun canonico(testo: String): String = buildString {
        var da = 0
        RIFERIMENTO.findAll(testo).forEach { m ->
            val numero = m.groupValues.drop(1).first { it.isNotEmpty() }.toIntOrNull()?.takeIf { it in 1..MASSIMO_VOCE }
            append(letterale(testo.substring(da, m.range.first)))
            append(if (numero != null) "{V$numero}" else letterale(m.value))
            da = m.range.last + 1
        }
        append(letterale(testo.substring(da)))
    }

    private fun letterale(s: String): String = s.replace("{", "{{").replace("}", "}}")
}
