package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/** What survives the Verifica delle fonti (INV-S4); dropped texts are never kept, only [omessi]. */
internal data class EsitoVerifica(
    val sommario: Sommario?,
    val decisioni: List<Decisione>,
    val questioniAperte: List<QuestioneAperta>,
    val azioni: List<Azione>,
    val puntiChiave: List<PuntoChiave>,
    val omessi: Int,
) {
    val vuoto: Boolean get() = sommario == null &&
        decisioni.isEmpty() && questioniAperte.isEmpty() && azioni.isEmpty() && puntiChiave.isEmpty()
}

/**
 * INV-I10 (amends INV-S4), applied by [Riassunto.completa] to the raw answer against the Incontro structure read for
 * the run: each label is mapped through the run's [etichette] (label k ↔ `etichette[k-1]`); a label outside 1…N, or a
 * Segmento that is not in its Parte's Trascritto as read, is an invalid Fonte, dropped; duplicates collapse; an element
 * with no valid Fonte, or with an invalid/malformed speaker token, is dropped and counted; a Sommario with such a token
 * is dropped and counted; a Responsabile that is not a Voce of the Incontro, or a PuntoChiave speaker that is not the
 * Voce of one of its valid Fonti (in any Parte), is unbound (element kept).
 */
internal class VerificaDelleFonti(
    private val struttura: StrutturaIncontro,
    private val etichette: List<SegmentoRef>,
) {
    private val voci = struttura.voci
    private var omessi = 0

    fun applica(bozza: BozzaRiassunto): EsitoVerifica {
        val sommario = bozza.sommario?.takeIf { it.isNotBlank() }?.let { s -> testoValido(s)?.let(::Sommario) }
        val decisioni = bozza.decisioni.mapNotNull { e -> verifica(e) { t, f -> Decisione(t, f) } }
        val questioniAperte = bozza.questioniAperte.mapNotNull { e -> verifica(e) { t, f -> QuestioneAperta(t, f) } }
        val azioni = bozza.azioni.mapNotNull { e ->
            verifica(e) { t, f -> Azione(t, f, responsabile = e.voce?.let(::VoceId)?.takeIf { it in voci }) }
        }
        val puntiChiave = bozza.puntiChiave.mapNotNull { e ->
            verifica(e) { t, f ->
                PuntoChiave(t, f, parlante = e.voce?.let(::VoceId)?.takeIf { v -> f.any { struttura.voceDi(it) == v } })
            }
        }
        return EsitoVerifica(sommario, decisioni, questioniAperte, azioni, puntiChiave, omessi)
    }

    private fun <E> verifica(elemento: BozzaElemento, crea: (TestoConVoci, Set<SegmentoRef>) -> E): E? {
        val fonti = elemento.fonti.mapNotNull { k -> etichette.getOrNull(k - 1) }.filter(struttura::contiene).toSet()
        if (fonti.isEmpty()) {
            omessi++
            return null
        }
        // A29: a blank testo is "nothing drafted" like a blank Sommario (below) — dropped, not counted; only a
        // malformed/invalid token (testoValido) is dropped AND counted.
        return elemento.testo.takeIf { it.isNotBlank() }?.let { t -> testoValido(t)?.let { crea(it, fonti) } }
    }

    /** The decoded text if every token is well-formed and a Voce of the structure; else counts it and null. */
    private fun testoValido(s: String): TestoConVoci? {
        val testo = TestoConVoci.decodifica(s)?.takeIf { voci.containsAll(it.voci) }
        if (testo == null) omessi++
        return testo
    }
}
