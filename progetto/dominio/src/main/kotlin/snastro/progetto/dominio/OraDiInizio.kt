package snastro.progetto.dominio

import snastro.kernel.Esito
import java.time.LocalTime

/**
 * The local wall-clock time of day at which a Parte starts, to the second, no time zone (ADR 0033 §1).
 * INV-I14: always in [00:00:00, 24:00:00); an unknown time is the ABSENCE of an OraDiInizio (`null`), legal also after
 * an edit.
 */
@JvmInline
public value class OraDiInizio private constructor(public val valore: LocalTime) {
    public companion object {
        private val FORMATO = Regex("""([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])""")

        /** INV-I14: [ora] to the second; a fraction of a second → [ErroreProgetto.OraDiInizioNonValida]. */
        public fun di(ora: LocalTime): Esito<OraDiInizio> =
            if (ora.nano == 0) Esito.Ok(OraDiInizio(ora)) else Esito.Errore(ErroreProgetto.OraDiInizioNonValida)

        /**
         * INV-I14 from user text `HH:mm:ss`: `null` → `Ok(null)`, the empty time; anything that is not a time of day in
         * [00:00:00, 24:00:00) (e.g. `24:00:00`, `12:60:00`, `""`) → [ErroreProgetto.OraDiInizioNonValida].
         * Strict: no trimming, no padding (`" 09:05:00"`, `"9:05:00"` are refused) — it also reads the stored text, so
         * a UI maps its own input (blank → `null`, trim, pad) before calling it.
         */
        public fun di(testo: String?): Esito<OraDiInizio?> {
            val parti = testo?.let { FORMATO.matchEntire(it)?.destructured }
            return when {
                testo == null -> Esito.Ok(null)
                parti == null -> Esito.Errore(ErroreProgetto.OraDiInizioNonValida)
                else -> {
                    val (ore, minuti, secondi) = parti
                    Esito.Ok(OraDiInizio(LocalTime.of(ore.toInt(), minuti.toInt(), secondi.toInt())))
                }
            }
        }
    }
}
