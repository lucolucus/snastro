package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.RichiestaRiassunto

/**
 * The instruction prompt the adapter owns (ADR 0021 §4, ADR 0026 §3): Qwen's ChatML written by hand with an EMPTY
 * `<think></think>` (thinking off; it matches the model's jinja template, spike: identical token counts). The system
 * turn asks for the Sommario alone, as `{"sommario": "…"}` ([GrammaticaRisposta]), with the `{V<n>}` speaker syntax, a
 * length ([MisuraRisposta]) and — when present — l'Argomento as context, never as a filter. It is pass 1 of spike H1
 * word for word (`.mismagent/spikes/h1.md`, R1-8): the only answer that, on skatefest, told the key points in every run
 * and never invented one. The user turn is the labelled input built by Sintesi (`IngressoRiassunto`: lines
 * `[s<n> V<n>] testo`, then the legend `V<n> = Voce n`, ADR 0032). The system text still says "V<n> = nome": it is the
 * wording measured with the good results, left as is on purpose.
 */
internal object PromptRiassunto {
    /**
     * ChatML-style control markers (`<|…|>`, e.g. `<|im_start|>`, `<|im_end|>`, `<|endoftext|>`): removed from
     * every inserted text so it can never close or open a turn, nor smuggle another special token in.
     */
    private val MARCATORE = Regex("""<\|[^|]*\|>""")

    fun componi(richiesta: RichiestaRiassunto): String =
        "<|im_start|>system\n${istruzioni(richiesta)}<|im_end|>\n" +
            "<|im_start|>user\n${neutro(richiesta.ingresso)}<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"

    private fun istruzioni(richiesta: RichiestaRiassunto): String = buildString {
        append(
            """
            Sei un assistente che riassume trascrizioni di riunioni di lavoro, in italiano.
            La trascrizione è automatica: contiene errori di riconoscimento, frasi spezzate e parti in inglese.
            Ogni riga ha la forma [s<numero> V<n>] testo. In fondo c'è la legenda dei parlanti: righe V<n> = nome.
            Regole:
            - Scrivi tutto in italiano. Non scrivere mai i nomi dei parlanti: nei testi indica un parlante SOLO come {V<n>}, ad esempio {V2}.
            - Ignora chiacchiere, battute e argomenti che non c'entrano con lo scopo della riunione.
            """.trimIndent(),
        )
        append("\n- Rispondi con un JSON {\"sommario\": \"...\"}: il sommario è un testo di circa ")
        append("${MisuraRisposta.di(richiesta).paroleSommario} parole che racconta la riunione nell'ordine in cui si ")
        append("è svolta.")
        richiesta.argomento?.let { argomento ->
            append("\n- Argomento della riunione indicato dall'utente: \"${neutro(argomento)}\". ")
            append("Usalo per capire di cosa parla la riunione; racconta comunque tutta la riunione.")
        }
    }

    /**
     * Strips [MARCATORE] to a fixed point, not just once: removing an inner marker can reveal an outer one that
     * did not match before (`"<|im_star" + "<|im_end|>" + "t|>"` has no `<|im_start|>` until the `<|im_end|>` in
     * the middle is gone) — a single pass would leave that reconstructed marker in place, so `neutro` would not
     * be idempotent and a crafted input could still smuggle a live marker through.
     */
    private tailrec fun neutro(testo: String): String {
        val ripulito = MARCATORE.replace(testo, "")
        return if (ripulito == testo) testo else neutro(ripulito)
    }
}
