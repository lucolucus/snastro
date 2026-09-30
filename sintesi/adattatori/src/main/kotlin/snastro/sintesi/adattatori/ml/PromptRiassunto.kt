package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.RichiestaRiassunto

/**
 * The instruction prompt the adapter owns (ADR 0021 §4, ADR 0026 §3): Qwen's ChatML written by hand with an EMPTY
 * `<think></think>` (thinking off; it matches the model's jinja template, spike: identical token counts). The system
 * turn carries the rules of answer schema v1, the `{V<n>}` speaker syntax, the length ([MisuraRisposta]: a target
 * proportional to the transcript, the cap as its ceiling) and — when present — the
 * Argomento instruction (wording provisional until spike `filtro-fuori-tema`); the user turn is the labelled input
 * built by Sintesi (`IngressoRiassunto`: lines `[s<n> V<n>] testo`, then the legend `V<n> = Voce n`, ADR 0032). The
 * system text still says "V<n> = nome": it is the wording measured with the good results, left as is on purpose.
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
            - Ogni decisione, questione aperta, azione e punto chiave deve citare in "fonti" i numeri delle righe (es. 12 per s12) da cui deriva. Usa SOLO numeri presenti.
            - "parlante" e "responsabile" sono il numero n di un V<n> della legenda, oppure null se non è chiaro.
            - Una decisione è qualcosa su cui il gruppo ha concordato; una questione aperta è qualcosa rimasto da decidere; un'azione è un compito che qualcuno deve fare. Se non ce ne sono, lascia la lista vuota: non inventare.
            """.trimIndent(),
        )
        val misura = MisuraRisposta.di(richiesta)
        append("\n- Lunghezza: scrivi circa ${misura.obiettivoParole} parole in tutto, mai più di ")
        append("${richiesta.lunghezzaMassimaParole}. Il sommario è un testo di circa ${misura.paroleSommario} parole ")
        append("che racconta la riunione nell'ordine in cui si è svolta. Poi riporta decisioni, questioni aperte, ")
        append("azioni e punti chiave: ognuno in una o due frasi, con il contesto che serve a capirlo senza ")
        append("leggere la trascrizione. Includi solo voci distinte che compaiono davvero nella trascrizione: non ")
        append("ripetere mai una voce, né la stessa cosa con altre parole, e non riportare nei punti chiave ciò che è ")
        append("già tra le decisioni. Il massimo è ${misura.massimoVoci} voci per tipo, ma è un limite, non un ")
        append("obiettivo: meglio poche voci che voci ripetute.")
        richiesta.argomento?.let { argomento ->
            append("\n- Argomento della riunione indicato dall'utente: \"${neutro(argomento)}\". ")
            append("Riassumi solo ciò che riguarda questo argomento; tutto il resto è fuori tema e va tralasciato.")
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
