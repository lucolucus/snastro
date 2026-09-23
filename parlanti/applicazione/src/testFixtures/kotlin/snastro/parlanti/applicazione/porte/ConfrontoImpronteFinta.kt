package snastro.parlanti.applicazione.porte

import snastro.parlanti.dominio.Impronta

/**
 * Scripted [ConfrontoImpronte]: a print equal to the Voce's is [Fascia.FORTE]; any other has the band
 * [programmate] assigns to it (default [Fascia.NESSUNA]); the result is the best over the list. Non-comparable
 * prints and dimension mismatches are [Fascia.NESSUNA], as the port declares.
 */
public class ConfrontoImpronteFinta(private val programmate: Map<Impronta, Fascia> = emptyMap()) : ConfrontoImpronte {
    override fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia =
        if (!confrontabile(voce)) {
            Fascia.NESSUNA
        } else {
            impronte.minOfOrNull { fasciaDi(voce, it) } ?: Fascia.NESSUNA
        }

    private fun fasciaDi(voce: Impronta, impronta: Impronta): Fascia =
        when {
            !confrontabile(impronta) || impronta.dimensione != voce.dimensione -> Fascia.NESSUNA
            impronta == voce -> Fascia.FORTE
            else -> programmate[impronta] ?: Fascia.NESSUNA
        }

    private fun confrontabile(i: Impronta): Boolean {
        val indici = 0 until i.dimensione
        return indici.all { i[it].isFinite() } && indici.any { i[it] != 0f }
    }
}
