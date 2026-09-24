package snastro.parlanti.adattatori.ml

import snastro.parlanti.applicazione.porte.ConfrontoImpronte
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.SoglieFascia
import snastro.parlanti.dominio.Impronta
import kotlin.math.sqrt

/**
 * Real [ConfrontoImpronte] (boundary `tec-confronto-impronte`, ADR 0004): cosine similarity, banded by
 * the injected [soglie] (AC-127: passes `ConfrontoImpronteContratto` in the gate, without any model —
 * pure Kotlin, no `:ml-sherpa`). The BEST band over [impronte] (an empty list is [Fascia.NESSUNA]); a
 * pair that isn't comparable (a zero-norm or non-finite print, or a dimension mismatch) counts as
 * [Fascia.NESSUNA] without throwing.
 *
 * [SOGLIA_FORTE_PROVVISORIA] / [SOGLIA_DEBOLE_PROVVISORIA]: TODO placeholder thresholds — spike
 * `impronta-vocale-affidabilita` calibrates the real values in its closing ADR.
 */
public class ConfrontoImpronteCoseno(
    private val soglie: SoglieFascia = SoglieFascia(SOGLIA_FORTE_PROVVISORIA, SOGLIA_DEBOLE_PROVVISORIA),
) : ConfrontoImpronte {
    override fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia =
        impronte.minOfOrNull { fasciaDi(voce, it) } ?: Fascia.NESSUNA

    private fun fasciaDi(voce: Impronta, impronta: Impronta): Fascia {
        val somiglianza = coseno(voce, impronta) ?: return Fascia.NESSUNA
        return when {
            somiglianza >= soglie.forte -> Fascia.FORTE
            somiglianza >= soglie.debole -> Fascia.DEBOLE
            else -> Fascia.NESSUNA
        }
    }

    /** `null` when the pair isn't comparable: dimension mismatch, non-finite value, or a zero-norm print. */
    private fun coseno(a: Impronta, b: Impronta): Double? {
        if (a.dimensione == 0 || a.dimensione != b.dimensione) return null
        var prodotto = 0.0
        var normaA = 0.0
        var normaB = 0.0
        var finito = true
        for (i in 0 until a.dimensione) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            if (!x.isFinite() || !y.isFinite()) finito = false
            prodotto += x * y
            normaA += x * x
            normaB += y * y
        }
        return if (!finito || normaA == 0.0 || normaB == 0.0) null else prodotto / (sqrt(normaA) * sqrt(normaB))
    }

    public companion object {
        public const val SOGLIA_FORTE_PROVVISORIA: Double = 0.7
        public const val SOGLIA_DEBOLE_PROVVISORIA: Double = 0.5
    }
}
