package snastro.parlanti.dominio

import snastro.kernel.Esito
import java.util.Locale

/** The name of a Parlante, trimmed. [normalizzato] is the INV-16 uniqueness key (ADR 0007). */
public class Nome internal constructor(public val valore: String) {
    public val normalizzato: String get() = valore.trim().lowercase(Locale.ROOT)

    override fun equals(other: Any?): Boolean = other is Nome && other.valore == valore

    override fun hashCode(): Int = valore.hashCode()

    override fun toString(): String = "Nome($valore)"

    public companion object {
        public fun di(testo: String): Esito<Nome> =
            if (testo.isBlank()) Esito.Errore(ErroreParlanti.NomeVuoto) else Esito.Ok(Nome(testo.trim()))
    }
}
