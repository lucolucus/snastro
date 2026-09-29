package snastro.sintesi.adattatori.ml

/**
 * Re-expresses a text written by the model in the port's canonical form (ADR 0021 §4, the `TestoConVoci` codec):
 * every speaker reference the prompt's syntax admits — `{V<n>}` (the one asked for), `{ V<n> }`, `[V<n>]`, a bare
 * `V<n>`, `Voce <n>` — becomes `{V<n>}`; every other brace is literal and is doubled. A number the codec cannot
 * carry (`0`, more than 9 digits) stays plain text.
 *
 * The two EXPLICIT forms (`{V<n>}`, `[V<n>]`) always count — the model was told to write speakers exactly
 * that way. The two BARE forms (`V<n>`, `Voce <n>`) count only when `n` is a real speaker of [legenda]: unlike
 * the explicit forms, plain text can contain them by accident ("motore V8", "la V2 del prototipo"), and turning
 * an unknown Voce into a reference can make the root's INV-S4 drop the whole legit element that carries it.
 */
internal object VociNelTesto {
    private val RIFERIMENTO = Regex("""\{\s*V(\d+)\s*}|\[V(\d+)]|\bVoce\s+(\d+)\b|\bV(\d+)\b""")
    private val VOCE_LEGENDA = Regex("""(?m)^V(\d+) = .*$""")
    private const val MASSIMO_VOCE = 999_999_999

    /** The `V<n>` numbers `IngressoRiassunto`'s legend lines (`V<n> = <nome>`, at the end of [ingresso]) name. */
    fun legenda(ingresso: String): Set<Int> =
        VOCE_LEGENDA.findAll(ingresso).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

    fun canonico(testo: String, legenda: Set<Int>): String = buildString {
        var da = 0
        RIFERIMENTO.findAll(testo).forEach { m ->
            val esplicito = m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty()
            val numero = m.groupValues.drop(1).first { it.isNotEmpty() }.toIntOrNull()?.takeIf { it in 1..MASSIMO_VOCE }
            val valido = numero != null && (esplicito || numero in legenda)
            append(letterale(testo.substring(da, m.range.first)))
            append(if (valido) "{V$numero}" else letterale(m.value))
            da = m.range.last + 1
        }
        append(letterale(testo.substring(da)))
    }

    private fun letterale(s: String): String = s.replace("{", "{{").replace("}", "}}")
}
