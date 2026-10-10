package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.RispostaModello

/**
 * The answer the bounded grammar ([GrammaticaRisposta]) makes the model write — `{"sommario": "…"}` (R1-8) — read into
 * the port's raw [RispostaModello], whose four lists stay empty. The Sommario is split into paragraphs
 * ([ParagrafiSommario]) and re-expressed with [VociNelTesto].
 */
internal object RispostaSommario {
    /**
     * The answer in [json], or `null` when it is not exactly one string under `sommario`. [legenda]
     * (`VociNelTesto.legenda` of the request's `ingresso`) gates the bare `V<n>` / `Voce <n>` rewrite.
     */
    fun leggi(json: String, legenda: Set<Int>): RispostaModello? {
        val radice = JsonMinimo.leggi(json).getOrNull() as? Map<*, *>
        val sommario = radice?.takeIf { it.keys == setOf("sommario") }?.get("sommario") as? String ?: return null
        return RispostaModello(
            sommario = VociNelTesto.canonico(ParagrafiSommario.dividi(sommario), legenda),
            decisioni = emptyList(),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
    }
}
