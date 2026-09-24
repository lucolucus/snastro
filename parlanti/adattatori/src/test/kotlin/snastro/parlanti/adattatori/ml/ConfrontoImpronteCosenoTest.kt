package snastro.parlanti.adattatori.ml

import snastro.parlanti.applicazione.porte.ConfrontoImpronte
import snastro.parlanti.applicazione.porte.ConfrontoImpronteContratto
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.SoglieFascia
import snastro.parlanti.dominio.Impronta
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ConfrontoImpronteCoseno] against the consumer-driven contract (AC-127: it passes
 * [ConfrontoImpronteContratto] in the gate, no model involved — pure Kotlin) plus AC-128, which pins
 * the exact banding at the provisional thresholds.
 */
class ConfrontoImpronteCosenoTest : ConfrontoImpronteContratto() {
    override fun confronto(): ConfrontoImpronte = ConfrontoImpronteCoseno()

    @Test
    fun `AC-128 con soglie forte 0_7 e debole 0_5 la similarita banda correttamente e conta la migliore`() {
        val confronto = ConfrontoImpronteCoseno(SoglieFascia(forte = 0.7, debole = 0.5))
        val voce = Impronta(floatArrayOf(1f, 0f))

        assertEquals(Fascia.FORTE, confronto.fascia(voce, listOf(impronta(0.8))))
        assertEquals(Fascia.DEBOLE, confronto.fascia(voce, listOf(impronta(0.6))))
        assertEquals(Fascia.NESSUNA, confronto.fascia(voce, listOf(impronta(0.3))))
        assertEquals(
            Fascia.FORTE,
            confronto.fascia(voce, listOf(impronta(0.3), impronta(0.6), impronta(0.8))),
            "conta la fascia migliore tra le impronte",
        )
    }

    /** A unit 2D print whose cosine similarity with `(1, 0)` is exactly [similarita]. */
    private fun impronta(similarita: Double): Impronta {
        val y = sqrt(1.0 - similarita * similarita)
        return Impronta(floatArrayOf(similarita.toFloat(), y.toFloat()))
    }
}
