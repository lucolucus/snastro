package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
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
 * INV-S4, applied by [Riassunto.completa] to the raw answer against the structure read for the run:
 * invalid Fonti dropped and duplicates collapsed; an element with no valid Fonte, or with an invalid/malformed
 * speaker token, dropped and counted; a Sommario with such a token dropped and counted; an invalid Responsabile /
 * PuntoChiave speaker binding removed (element kept).
 */
internal class VerificaDelleFonti(private val struttura: StrutturaTrascritto) {
    private var omessi = 0

    fun applica(bozza: BozzaRiassunto): EsitoVerifica {
        val sommario = bozza.sommario?.takeIf { it.isNotBlank() }?.let { s -> testoValido(s)?.let(::Sommario) }
        val decisioni = bozza.decisioni.mapNotNull { e -> verifica(e) { t, f -> Decisione(t, f) } }
        val questioniAperte = bozza.questioniAperte.mapNotNull { e -> verifica(e) { t, f -> QuestioneAperta(t, f) } }
        val azioni = bozza.azioni.mapNotNull { e ->
            verifica(e) { t, f -> Azione(t, f, responsabile = e.voce?.let(::VoceId)?.takeIf { it in struttura.voci }) }
        }
        val puntiChiave = bozza.puntiChiave.mapNotNull { e ->
            verifica(e) { t, f ->
                PuntoChiave(t, f, parlante = e.voce?.let(::VoceId)?.takeIf { v -> f.any { struttura.voceDi(it) == v } })
            }
        }
        return EsitoVerifica(sommario, decisioni, questioniAperte, azioni, puntiChiave, omessi)
    }

    private fun <E> verifica(elemento: BozzaElemento, crea: (TestoConVoci, Set<SegmentoId>) -> E): E? {
        val fonti = elemento.fonti.map(::SegmentoId).filter(struttura::contiene).toSet()
        if (fonti.isEmpty()) {
            omessi++
            return null
        }
        return testoValido(elemento.testo)?.let { crea(it, fonti) }
    }

    /** The decoded text if every token is well-formed and a Voce of the structure; else counts it and null. */
    private fun testoValido(s: String): TestoConVoci? {
        val testo = TestoConVoci.decodifica(s)?.takeIf { struttura.voci.containsAll(it.voci) }
        if (testo == null) omessi++
        return testo
    }
}
