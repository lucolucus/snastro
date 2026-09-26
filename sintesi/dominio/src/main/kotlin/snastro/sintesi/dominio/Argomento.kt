package snastro.sintesi.dominio

import snastro.kernel.Esito

/** What the recording is about (AC-S1): trimmed, never blank, at most [MASSIMO_CARATTERI] characters. */
@JvmInline
public value class Argomento private constructor(public val valore: String) {
    public companion object {
        /** Provisional (spike filtro-fuori-tema): the single home of the bound. */
        public const val MASSIMO_CARATTERI: Int = 200

        /** Blank or null → `Ok(null)` (no Argomento); too long → [ErroreSintesi.ArgomentoTroppoLungo]. */
        public fun di(testo: String?): Esito<Argomento?> {
            val pulito = testo?.trim().orEmpty()
            return when {
                pulito.isEmpty() -> Esito.Ok(null)
                pulito.length > MASSIMO_CARATTERI ->
                    Esito.Errore(ErroreSintesi.ArgomentoTroppoLungo(pulito.length, MASSIMO_CARATTERI))
                else -> Esito.Ok(Argomento(pulito))
            }
        }
    }
}
