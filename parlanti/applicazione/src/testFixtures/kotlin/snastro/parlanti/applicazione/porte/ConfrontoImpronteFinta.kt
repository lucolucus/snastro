package snastro.parlanti.applicazione.porte

import snastro.parlanti.dominio.Impronta

/**
 * Scripted [ConfrontoImpronte]: a print equal to the Voce's is [Fascia.FORTE]; any other has the band
 * [programmate] assigns to it (default [Fascia.NESSUNA]); the result is the best over the list.
 */
public class ConfrontoImpronteFinta(private val programmate: Map<Impronta, Fascia> = emptyMap()) : ConfrontoImpronte {
    override fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia =
        impronte.minOfOrNull { if (it == voce) Fascia.FORTE else programmate[it] ?: Fascia.NESSUNA } ?: Fascia.NESSUNA
}
