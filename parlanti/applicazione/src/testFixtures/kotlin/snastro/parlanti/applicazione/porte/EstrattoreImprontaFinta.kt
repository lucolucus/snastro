package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.parlanti.dominio.Impronta

/**
 * Deterministic [EstrattoreImpronta]: component `k` of the [dimensione]-long print is the mean of the samples
 * at indices `≡ k (mod dimensione)`. Different audio → (usually) different prints; the input is only read.
 */
public class EstrattoreImprontaFinta(private val dimensione: Int = DIMENSIONE) : EstrattoreImpronta {
    init {
        require(dimensione > 0) { "dimensione non positiva: $dimensione" }
    }

    override fun estrai(c: CampioniAudio): Impronta {
        val somme = FloatArray(dimensione)
        val conteggi = IntArray(dimensione)
        c.campioni.forEachIndexed { i, v ->
            somme[i % dimensione] += v
            conteggi[i % dimensione]++
        }
        return Impronta(FloatArray(dimensione) { k -> if (conteggi[k] == 0) 0f else somme[k] / conteggi[k] })
    }

    private companion object {
        const val DIMENSIONE = 8
    }
}
