package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.parlanti.dominio.Impronta

/**
 * Scripted [ClassificatoreSomiglianza]: [programmate] maps a frase's index in [classifica]'s
 * `frasi` to the [Classificazione] to return for it (default [Classificazione.Incerta] for an
 * unlisted index). Every call is recorded in [chiamate] (the `riferimenti` map received, verbatim)
 * so a test can assert what a consumer passed in, without any real embedding math.
 */
public class ClassificatoreSomiglianzaFinta(
    private val programmate: Map<Int, Classificazione> = emptyMap(),
) : ClassificatoreSomiglianza {
    private val _chiamate = mutableListOf<Map<ParlanteId, List<Impronta>>>()

    /** Every `riferimenti` map this fake received, in call order. */
    public val chiamate: List<Map<ParlanteId, List<Impronta>>> get() = _chiamate.toList()

    override fun classifica(riferimenti: Map<ParlanteId, List<Impronta>>, frasi: List<Impronta>): List<Classificazione> {
        require(riferimenti.size >= 2) { "servono almeno 2 Parlanti di riferimento: ${riferimenti.size}" }
        require(riferimenti.values.all { it.isNotEmpty() }) { "ogni Parlante di riferimento ha almeno una impronta" }
        _chiamate += riferimenti
        return frasi.indices.map { i -> programmate[i] ?: Classificazione.Incerta }
    }
}
