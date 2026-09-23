package snastro.parlanti.dominio

/**
 * The embedding of one `Voce` (biometric, ADR 0009). Equal by content (CR-5). The array is not
 * copied: treat it as read-only. [toString] never prints the values (RC-6).
 */
public class Impronta(public val valori: FloatArray) {
    override fun equals(other: Any?): Boolean = other is Impronta && other.valori.contentEquals(valori)

    override fun hashCode(): Int = valori.contentHashCode()

    override fun toString(): String = "Impronta(${valori.size} valori)"
}
