package snastro.trascrizione.dominio

import snastro.kernel.Esito

/**
 * "Numero di persone": how many people the utente says speak in a Registrazione, 1..10 (ADR 0014). A hint to
 * diarization (the exact number of Voci to form), not a count of Parlanti. Built only through [di].
 */
@JvmInline
public value class NumeroPersone private constructor(public val valore: Int) {
    public companion object {
        private val INTERVALLO = 1..10

        public fun di(n: Int): Esito<NumeroPersone> =
            if (n in INTERVALLO) {
                Esito.Ok(NumeroPersone(n))
            } else {
                Esito.Errore(ErroreTrascrizione.NumeroPersoneFuoriIntervallo(n))
            }
    }
}
