package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.parlanti.dominio.Impronta

/**
 * Deterministic [EstrattoreImpronta]: component `k` of the [dimensione]-long print is the mean of the samples
 * at indices `≡ k (mod dimensione)`. Different audio → (usually) different prints; the input is only read.
 *
 * Given the command's [unitaDiLavoro], [estrai] throws [IllegalStateException] while it has a transaction
 * open (ADR 0012 Amendment (b): no ML inside a transaction, AC-272); without it no guard applies.
 */
public class EstrattoreImprontaFinta(
    private val dimensione: Int = DIMENSIONE,
    private val unitaDiLavoro: UnitaDiLavoroFinta? = null,
    override val modello: String = MODELLO,
) : EstrattoreImpronta {
    init {
        require(dimensione > 0) { "dimensione non positiva: $dimensione" }
    }

    override fun estrai(c: CampioniAudio): Impronta {
        check(unitaDiLavoro?.transazioneAperta != true) { "estrai invocato dentro una transazione (ADR 0012 (b))" }
        val somme = FloatArray(dimensione)
        val conteggi = IntArray(dimensione)
        c.campioni.forEachIndexed { i, v ->
            somme[i % dimensione] += v
            conteggi[i % dimensione]++
        }
        return Impronta(FloatArray(dimensione) { k -> if (conteggi[k] == 0) 0f else somme[k] / conteggi[k] })
    }

    public companion object {
        /** Default [modello] of the fake. */
        public const val MODELLO: String = "finto"
        private const val DIMENSIONE = 8
    }
}
