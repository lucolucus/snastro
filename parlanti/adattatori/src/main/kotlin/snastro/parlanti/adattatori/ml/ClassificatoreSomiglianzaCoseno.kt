package snastro.parlanti.adattatori.ml

import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianza
import snastro.parlanti.applicazione.porte.Classificazione
import snastro.parlanti.applicazione.porte.SoglieSomiglianza
import snastro.parlanti.dominio.Impronta
import kotlin.math.sqrt

/**
 * Real [ClassificatoreSomiglianza] (boundary `tec-classificatore-somiglianza`, ADR 0019 §4.3): pure
 * Kotlin cosine classifier, no native model (AC-496: passes `ClassificatoreSomiglianzaContratto` in
 * the gate, no `:ml-sherpa` involved). One centroid per reference Parlante — the L2-normalized mean
 * of its normalized, comparable reference [Impronta]s (a non-comparable reference is ignored inside
 * its centroid; a Parlante left with none comparable is left out of the ranking, AC-499). For each
 * frase, `best`/`second` are the two highest cosine scores among the centroids: [Classificazione.Sicura]
 * of the best Parlante iff `best >= soglie.minima` AND `best - second >= soglie.margine` (a missing
 * second — a single ranked centroid — trivially satisfies the margin); otherwise
 * [Classificazione.Incerta], which also covers a tie and a non-comparable frase (zero vector,
 * NaN/infinite value, dimension mismatch). Never throws on print data; no similarity number ever
 * leaves this adapter (code review, ADR 0019 §4.3 "Consequences").
 */
public class ClassificatoreSomiglianzaCoseno(
    private val soglie: SoglieSomiglianza = SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO),
) : ClassificatoreSomiglianza {
    override fun classifica(
        riferimenti: Map<ParlanteId, List<Impronta>>,
        frasi: List<Impronta>,
    ): List<Classificazione> {
        require(riferimenti.size >= 2) { "servono almeno 2 Parlanti di riferimento: ${riferimenti.size}" }
        require(riferimenti.values.all { it.isNotEmpty() }) { "ogni Parlante di riferimento ha almeno una impronta" }
        val centroidi = riferimenti.mapNotNull { (id, impronte) -> centroide(impronte)?.let { id to it } }
        return frasi.map { classificaUna(it, centroidi) }
    }

    private fun classificaUna(frase: Impronta, centroidi: List<Pair<ParlanteId, Impronta>>): Classificazione {
        val punteggi = centroidi.mapNotNull { (id, centroide) -> coseno(frase, centroide)?.let { id to it } }
            .sortedByDescending { (_, punteggio) -> punteggio }
        val migliore = punteggi.getOrNull(0) ?: return Classificazione.Incerta
        val secondo = punteggi.getOrNull(1)
        val sicura = migliore.second >= soglie.minima &&
            (secondo == null || migliore.second - secondo.second >= soglie.margine)
        return if (sicura) Classificazione.Sicura(migliore.first) else Classificazione.Incerta
    }

    /** L2-normalized mean of the comparable (finite, non-zero-norm) prints of [impronte]; `null` if none is. */
    private fun centroide(impronte: List<Impronta>): Impronta? {
        val normalizzate = impronte.mapNotNull { normalizza(it) }
        val dimensione = normalizzate.firstOrNull()?.dimensione
        return if (dimensione == null || normalizzate.any { it.dimensione != dimensione }) {
            null
        } else {
            val somma = DoubleArray(dimensione)
            for (v in normalizzate) for (i in 0 until dimensione) somma[i] += v[i]
            normalizza(Impronta(FloatArray(dimensione) { i -> (somma[i] / normalizzate.size).toFloat() }))
        }
    }

    /** `null` when [i] is not comparable: empty, holds a non-finite value, or has a zero norm. */
    private fun normalizza(i: Impronta): Impronta? {
        if (i.dimensione == 0) return null
        var normaQuadrata = 0.0
        var finita = true
        for (k in 0 until i.dimensione) {
            val v = i[k].toDouble()
            if (!v.isFinite()) finita = false
            normaQuadrata += v * v
        }
        return if (!finita || normaQuadrata == 0.0) {
            null
        } else {
            val norma = sqrt(normaQuadrata)
            Impronta(FloatArray(i.dimensione) { k -> (i[k] / norma).toFloat() })
        }
    }

    /** Cosine of [a] against the unit-norm [centroide]; `null` on a dimension mismatch or a non-finite value. */
    private fun coseno(a: Impronta, centroide: Impronta): Double? {
        if (a.dimensione == 0 || a.dimensione != centroide.dimensione) return null
        var prodotto = 0.0
        var normaA = 0.0
        var finita = true
        for (k in 0 until a.dimensione) {
            val x = a[k].toDouble()
            if (!x.isFinite()) finita = false
            prodotto += x * centroide[k]
            normaA += x * x
        }
        return if (!finita || normaA == 0.0) null else prodotto / sqrt(normaA)
    }

    public companion object {
        /** PROVISIONAL [hypothesis] (ADR 0019 §4.3): calibration pending (Via Roquel run, recorded by amendment). */
        public const val SIMILARITA_MINIMA: Double = 0.30

        /** PROVISIONAL [hypothesis] (ADR 0019 §4.3): calibration pending (Via Roquel run, recorded by amendment). */
        public const val MARGINE_MINIMO: Double = 0.05
    }
}
