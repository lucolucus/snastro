package snastro.avvio.accettazione

import snastro.ui.riassunto.ContenutoUi
import snastro.ui.riassunto.testoRiassuntoDaCopiare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * R1 · solo sommario (EX-20, EX-21, EX-26): Riassumi on S3's Riassunto tab gives a Riassunto with the Sommario alone —
 * no Decisioni, Questioni aperte, Azioni or Punti chiave, so no section heading and no Fonte chip — and Copia copies
 * that Sommario alone. Bench and substituted boundaries: [BancoRiassunto].
 */
class RiassuntoSoloSommarioTest : BancoRiassunto() {
    @Test
    fun `EX-20 skatefest con l'argomento di EX-14 da un riassunto pronto col solo sommario`() {
        val contenuto = schedaRiassunto().riassumi(ARGOMENTO_EX_14)

        assertSoloSommario(contenuto)
        assertTrue(ARGOMENTO_EX_14 in libreria.prompt.single(), "l'argomento arriva al modello")
    }

    @Test
    fun `EX-21 skatefest senza argomento da come EX-20 il solo sommario`() {
        val contenuto = schedaRiassunto().riassumi(argomento = null)

        assertSoloSommario(contenuto)
        assertTrue("Argomento" !in libreria.prompt.single(), "nessun argomento nel prompt")
    }

    @Test
    fun `EX-26 Copia del riassunto di EX-20 mette negli appunti il testo del solo sommario`() {
        val contenuto = schedaRiassunto().riassumi(ARGOMENTO_EX_14)

        val copiato = testoRiassuntoDaCopiare(contenuto)

        assertEquals(contenuto.sommario, copiato)
        listOf("Decisioni", "Questioni aperte", "Azioni", "Punti chiave").forEach { assertTrue(it !in copiato, it) }
    }

    /** The Sommario is there; the four lists are empty, so the tab draws no section heading and no Fonte chip. */
    private fun assertSoloSommario(contenuto: ContenutoUi) {
        val sommario = assertNotNull(contenuto.sommario)
        assertTrue("due o tre settimane" in sommario && "movimento automatico" in sommario, sommario)
        assertEquals(emptyList(), contenuto.decisioni, "decisioni")
        assertEquals(emptyList(), contenuto.questioniAperte, "questioni aperte")
        assertEquals(emptyList(), contenuto.azioni, "azioni")
        assertEquals(emptyList(), contenuto.puntiChiave, "punti chiave")
    }

    private companion object {
        const val ARGOMENTO_EX_14 = "Parliamo di skatefest, gioco già definitivo da proporre a qualche editore ma " +
            "forse che richiede qualche modifica, per esempio al comportamento dei polizitti oppure della fase " +
            "chill. servono i punti"
    }
}
