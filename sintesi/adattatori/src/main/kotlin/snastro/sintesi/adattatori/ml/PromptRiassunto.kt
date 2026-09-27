package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.RichiestaRiassunto

/**
 * The instruction prompt the adapter owns (ADR 0021 §4, ADR 0026 §3): Qwen's ChatML written by hand with an EMPTY
 * `<think></think>` (thinking off; it matches the model's jinja template, spike: identical token counts). The system
 * turn carries the rules of answer schema v1, the `{V<n>}` speaker syntax, the word cap and — when present — the
 * Argomento instruction (wording provisional until spike `filtro-fuori-tema`); the user turn is the labelled input
 * built by Sintesi (`IngressoRiassunto`: lines `[s<n> V<n>] testo`, then the legend `V<n> = nome`).
 */
internal object PromptRiassunto {
    /** ChatML control markers: removed from the inserted texts so they can never close or open a turn. */
    private val MARCATORI = listOf("<|im_start|>", "<|im_end|>")

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
            - Lunghezza complessiva massima: ${richiesta.lunghezzaMassimaParole} parole. Sommario: al massimo 5 frasi.
            """.trimIndent(),
        )
        richiesta.argomento?.let { argomento ->
            append("\n- Argomento della riunione indicato dall'utente: \"${neutro(argomento)}\". ")
            append("Riassumi solo ciò che riguarda questo argomento; tutto il resto è fuori tema e va tralasciato.")
        }
    }

    private fun neutro(testo: String): String = MARCATORI.fold(testo) { t, m -> t.replace(m, "") }
}
