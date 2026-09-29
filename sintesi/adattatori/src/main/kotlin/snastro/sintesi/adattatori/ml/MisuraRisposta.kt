package snastro.sintesi.adattatori.ml

import snastro.sintesi.applicazione.porte.RichiestaRiassunto

/**
 * How long the model is asked to write (2026-09-30, user: "the Riassunto stays short"). The lunghezza massima is
 * the CAP; the TARGET is a share of what was said — [QUOTA_DEL_TRASCRITTO] of the transcript's words, rounded to
 * [GRANA] — never above the cap nor below [MINIMO_OBIETTIVO] (or the cap, if lower). The Sommario gets
 * [QUOTA_SOMMARIO] of the target; each list may hold one item per [PAROLE_PER_VOCE] words of the target, between
 * [MINIMO_VOCI] (the former fixed bound) and [MASSIMO_VOCI]. [GrammaticaRisposta] enforces the lists' bound.
 */
internal data class MisuraRisposta(val obiettivoParole: Int, val paroleSommario: Int, val massimoVoci: Int) {
    companion object {
        const val QUOTA_DEL_TRASCRITTO = 0.4
        const val QUOTA_SOMMARIO = 0.4
        const val GRANA = 50
        const val MINIMO_OBIETTIVO = 250
        const val PAROLE_PER_VOCE = 100
        const val MINIMO_VOCI = 6
        const val MASSIMO_VOCI = 40

        /** `[s12 V3] ` line labels of `IngressoRiassunto`: not words of the conversation. */
        private val ETICHETTA = Regex("""\[s\d+ V\d+]""")

        fun di(richiesta: RichiestaRiassunto): MisuraRisposta {
            val tetto = richiesta.lunghezzaMassimaParole
            val proporzionale = (paroleDelTrascritto(richiesta.ingresso) * QUOTA_DEL_TRASCRITTO / GRANA).toInt() * GRANA
            val obiettivo = proporzionale.coerceAtLeast(MINIMO_OBIETTIVO).coerceAtMost(tetto)
            return MisuraRisposta(
                obiettivoParole = obiettivo,
                paroleSommario = ((obiettivo * QUOTA_SOMMARIO / GRANA).toInt() * GRANA).coerceAtLeast(GRANA),
                massimoVoci = (obiettivo / PAROLE_PER_VOCE).coerceIn(MINIMO_VOCI, MASSIMO_VOCI),
            )
        }

        fun paroleDelTrascritto(ingresso: String): Int =
            ETICHETTA.replace(ingresso, " ").split(Regex("""\s+""")).count { it.isNotBlank() }
    }
}
