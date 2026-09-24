package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.parlanti.dominio.Impronta

/**
 * Pure classification of transient `Segmento` prints against reference Parlanti's centroids
 * (boundary `tec-classificatore-somiglianza`, ADR 0019 §4.3): one [Classificazione] per entry of
 * [frasi], in the SAME size and order. [riferimenti] has >= 2 keys, each with >= 1 [Impronta] — the
 * CALLER guarantees this (`require`, a programmer error otherwise). Never throws on print data: a
 * non-comparable frase (the zero vector, a NaN/infinite value, or a dimension mismatch with the
 * centroids) is always [Classificazione.Incerta]; a non-comparable reference is ignored inside its
 * Parlante's centroid, and a Parlante left with no comparable reference is left out of the ranking.
 * No similarity number ever leaves this port. Contract: `ClassificatoreSomiglianzaContratto`.
 */
public interface ClassificatoreSomiglianza {
    public fun classifica(riferimenti: Map<ParlanteId, List<Impronta>>, frasi: List<Impronta>): List<Classificazione>
}
