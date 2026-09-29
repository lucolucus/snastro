package snastro.sintesi.dominio

import snastro.kernel.Esito

/**
 * INV-S9: the lunghezza massima del Riassunto, in words, within [[MINIMO], [MASSIMO]] (default [PREDEFINITA]).
 * [di] is the only factory. Bounds provisional (spikes runtime-llm-in-app / qualita-riassunto): one home, here.
 */
@JvmInline
public value class LunghezzaMassimaParole private constructor(public val valore: Int) {
    public companion object {
        public const val MINIMO: Int = 300
        public const val MASSIMO: Int = 10_000
        public const val PREDEFINITA: Int = 2000

        public fun di(n: Int): Esito<LunghezzaMassimaParole> =
            if (n in MINIMO..MASSIMO) {
                Esito.Ok(LunghezzaMassimaParole(n))
            } else {
                Esito.Errore(ErroreSintesi.LunghezzaMassimaFuoriIntervallo(n, MINIMO, MASSIMO))
            }
    }
}
