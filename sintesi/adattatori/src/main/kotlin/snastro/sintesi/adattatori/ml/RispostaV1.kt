package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.PuntoChiaveRisposta
import snastro.sintesi.applicazione.porte.RispostaModello

/**
 * Answer schema v1 (provisional until spike `qualita-riassunto`'s ADR, ADR 0021 §4): the JSON the bounded grammar
 * ([GrammaticaRisposta]) makes the model write, read into the port's raw [RispostaModello]. Exactly the five keys
 * [CHIAVI]; elements `{testo, fonti}` (+ `responsabile` for azioni, `parlante` for punti_chiave, an integer or
 * `null`). Every text is re-expressed with [VociNelTesto]. The ids are NOT validated here: the root does ([INV-S4]).
 */
internal object RispostaV1 {
    val CHIAVI: List<String> = listOf("sommario", "decisioni", "questioni_aperte", "azioni", "punti_chiave")

    /**
     * The answer in [json], or `null` when it does not match schema v1. [legenda] (`VociNelTesto.legenda` of the
     * request's `ingresso`) gates the bare `V<n>` / `Voce <n>` rewrite of every text.
     */
    @Suppress("ReturnCount") // one guard per schema part, each rejecting the whole answer
    fun leggi(json: String, legenda: Set<Int>): RispostaModello? {
        val radice = JsonMinimo.leggi(json).getOrNull() as? Map<*, *> ?: return null
        if (radice.keys != CHIAVI.toSet()) return null
        val sommario = radice["sommario"]
        if (sommario != null && sommario !is String) return null
        return RispostaModello(
            sommario = (sommario as String?)?.let { VociNelTesto.canonico(it, legenda) },
            decisioni = elementi(radice["decisioni"], setOf(), legenda) { testo, fonti, _ ->
                ElementoRisposta(testo, fonti)
            } ?: return null,
            questioniAperte = elementi(radice["questioni_aperte"], setOf(), legenda) { testo, fonti, _ ->
                ElementoRisposta(testo, fonti)
            } ?: return null,
            azioni = elementi(radice["azioni"], setOf("responsabile"), legenda) { testo, fonti, voce ->
                AzioneRisposta(testo, fonti, voce)
            } ?: return null,
            puntiChiave = elementi(radice["punti_chiave"], setOf("parlante"), legenda) { testo, fonti, voce ->
                PuntoChiaveRisposta(testo, fonti, voce)
            } ?: return null,
        )
    }

    /**
     * Each element of the list [valore] (keys `testo`, `fonti` + [altre]), or `null` if one does not fit. An element
     * whose text repeats an earlier one of the same list ([chiaveTesto]) is dropped (2026-09-30: with a larger list
     * bound the model can loop, re-emitting the same items to fill it).
     */
    private fun <T> elementi(
        valore: Any?,
        altre: Set<String>,
        legenda: Set<Int>,
        crea: (String, List<Int>, Int?) -> T,
    ): List<T>? =
        (valore as? List<*>)?.map { elemento ->
            val campi = elemento as? Map<*, *> ?: return null
            if (campi.keys != setOf("testo", "fonti") + altre) return null
            val testo = campi["testo"] as? String ?: return null
            val fonti = (campi["fonti"] as? List<*>)?.map { intero(it) ?: return null } ?: return null
            val voce = altre.singleOrNull()?.let { chiave -> campi[chiave]?.let { intero(it) ?: return null } }
            val canonico = VociNelTesto.canonico(testo, legenda)
            chiaveTesto(canonico) to crea(canonico, fonti, voce)
        }?.distinctBy { it.first }?.map { it.second }

    /** Case, spacing and final punctuation do not make a different item. */
    private fun chiaveTesto(testo: String): String =
        testo.lowercase().replace(SPAZI, " ").trim().trimEnd('.', ';', '!', '?', ' ')

    private val SPAZI = Regex("""\s+""")

    private fun intero(v: Any?): Int? = (v as? Long)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
}
