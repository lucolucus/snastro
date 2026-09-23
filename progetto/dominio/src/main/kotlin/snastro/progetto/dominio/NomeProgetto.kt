package snastro.progetto.dominio

import snastro.kernel.Esito

/** Display name of a [Progetto]: trimmed, never empty. */
@JvmInline
public value class NomeProgetto internal constructor(public val valore: String) {
    public companion object {
        public fun di(testo: String): Esito<NomeProgetto> =
            if (testo.isBlank()) {
                Esito.Errore(ErroreProgetto.NomeProgettoVuoto)
            } else {
                Esito.Ok(NomeProgetto(testo.trim()))
            }
    }
}
