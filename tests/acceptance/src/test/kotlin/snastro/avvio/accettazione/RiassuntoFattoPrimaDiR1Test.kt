package snastro.avvio.accettazione

import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.ui.riassunto.ContenutoUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R1 · punti cardine (EX-25): a Riassunto made before R1 — Sommario and lists, each element with its Fonti — stays as
 * it was until it is regenerated: reopened, the tab shows its Sommario and lists with their Fonte chips; Riassumi
 * again replaces it with one that has the Sommario alone. The earlier Riassunto is stored in the real project file
 * as the app stored it before R1 ([registraRiassuntoPronto]). Bench and substituted boundaries: [BancoRiassunto].
 */
class RiassuntoFattoPrimaDiR1Test : BancoRiassunto() {
    @Test
    fun `EX-25 un riassunto fatto prima di R1 resta con liste e fonti finche non lo si rigenera col solo sommario`() {
        val id = registrazioneTrascritta()
        registraRiassuntoPronto(id, PRIMA_DI_R1)

        val scheda = schedaRiassunto(id)
        val riaperto = scheda.mostrato()

        assertFalse(riaperto.superato, "superato")
        assertTrue("combattimento a turni" in riaperto.sommario.orEmpty(), riaperto.sommario)
        assertEquals(listOf("Il combattimento resta a turni."), riaperto.decisioni.map { it.testo })
        assertEquals(listOf("Quanti nemici per stanza?"), riaperto.questioniAperte.map { it.testo })
        assertEquals(1, riaperto.azioni.size, "azioni")
        assertEquals(1, riaperto.puntiChiave.size, "punti chiave")
        assertEquals(listOf(1, 1, 1, 1), chipFonte(riaperto), "un chip fonte per elemento")

        val rigenerato = checkNotNull(
            scheda.riassumiFinche(argomento = null, "Riassunto rigenerato") {
                it.contenuto?.sommario?.contains("due o tre settimane") == true
            }.contenuto,
        )

        assertEquals(emptyList(), rigenerato.decisioni, "decisioni")
        assertEquals(emptyList(), rigenerato.questioniAperte, "questioni aperte")
        assertEquals(emptyList(), rigenerato.azioni, "azioni")
        assertEquals(emptyList(), rigenerato.puntiChiave, "punti chiave")
    }

    /** The Fonte chips the tab draws under each element, list by list. */
    private fun chipFonte(c: ContenutoUi): List<Int> =
        c.decisioni.map { it.fonti.size } + c.questioniAperte.map { it.fonti.size } +
            c.azioni.map { it.fonti.size } + c.puntiChiave.map { it.fonti.size }

    private companion object {
        /** What the model answered before R1, over the bench's two Segmenti (Fonte k = Segmento k). */
        val PRIMA_DI_R1 = BozzaRiassunto(
            sommario = "{V1} e {V2} tengono il combattimento a turni.",
            decisioni = listOf(BozzaElemento("Il combattimento resta a turni.", listOf(1), voce = null)),
            questioniAperte = listOf(BozzaElemento("Quanti nemici per stanza?", listOf(2), voce = null)),
            azioni = listOf(BozzaElemento("{V2} prepara il prototipo.", listOf(2), voce = 2)),
            puntiChiave = listOf(BozzaElemento("Il ritmo del combattimento.", listOf(1), voce = 1)),
        )
    }
}
