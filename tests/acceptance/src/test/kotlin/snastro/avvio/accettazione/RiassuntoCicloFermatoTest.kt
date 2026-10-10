package snastro.avvio.accettazione

import snastro.ui.riassunto.AreaAzione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R1 · ciclo (EX-18): when the model starts repeating itself, Riassumi no longer writes for 40 minutes — the
 * generation stops early, the Riassunto fails with "Il modello si è ripetuto: riprova." and Riprova, and the ready
 * Riassunto made before stays on screen. Bench and substituted boundaries: [BancoRiassunto]; the loop is the fake
 * model's [LibreriaCheSegueLaGrammatica.siRipete], timed at the real model's measured speed.
 */
class RiassuntoCicloFermatoTest : BancoRiassunto() {
    @Test
    fun `EX-18 un modello che si ripete al tetto di 10000 parole fallisce entro 600 s, Riprova, il precedente resta`() {
        val scheda = schedaRiassunto()
        val precedente = scheda.riassumi(argomento = null)
        scheda.impostaLunghezzaMassima(LUNGHEZZA_DELLO_SPIKE)
        libreria.siRipete = true

        val dati = scheda.riassumiFinche(argomento = null, "Riassunto fallito") { it.fallimentoTesto != null }

        assertEquals(AreaAzione.Fallito("Il modello si è ripetuto: riprova."), dati.areaAzione)
        assertEquals(precedente.sommario, dati.contenuto?.sommario, "il riassunto pronto precedente resta visibile")
        val secondi = libreria.secondiInCiclo.single()
        assertTrue(secondi <= LIMITE_SECONDI, "il ciclo si ferma entro $LIMITE_SECONDI s: $secondi s")
    }

    private companion object {
        /** AC-S153's bound for a whole Riassunto. */
        const val LIMITE_SECONDI = 600.0

        /** The lunghezza massima of spike H1's runs, v6's loop included. */
        const val LUNGHEZZA_DELLO_SPIKE = 10_000
    }
}
