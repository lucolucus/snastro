package snastro.avvio.accettazione

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * R1 · argomento (EX-23): l'Argomento helps the model understand the meeting but never takes a point away. With an
 * Argomento about the site, the model still receives the whole meeting, is told to tell all of it, and the tab shows
 * its whole Sommario. Bench and substituted boundaries: [BancoRiassunto]; what the real model writes on skatefest with
 * this Argomento (Amplify, D1 and Q1) is measured with `benchmarkRiassunto -Pargomento` (`.mismagent/spikes/h1.md`).
 */
class RiassuntoArgomentoComeContestoTest : BancoRiassunto() {
    @Test
    fun `EX-23 con un argomento sul sito il modello riceve tutta la riunione e il sommario mostrato e tutto il suo`() {
        val senzaArgomento = schedaRiassunto().riassumi(argomento = null)
        val conArgomento = schedaRiassunto().riassumi(ARGOMENTO_SUL_SITO)

        val (promptSenza, promptCon) = libreria.prompt
        assertEquals(riunioneNel(promptSenza), riunioneNel(promptCon), "l'argomento non toglie niente dalla riunione")
        assertTrue(ARGOMENTO_SUL_SITO in promptCon, "l'argomento arriva al modello")
        assertTrue("racconta comunque tutta la riunione" in promptCon, "l'argomento è contesto, non filtro")
        val sommario = assertNotNull(conArgomento.sommario)
        assertEquals(senzaArgomento.sommario, sommario, "il sommario del modello è mostrato intero")
        assertTrue("due o tre settimane" in sommario && "movimento automatico" in sommario, sommario)
    }

    private companion object {
        const val ARGOMENTO_SUL_SITO = "Parliamo solo del sito e dell'hosting"
    }
}
