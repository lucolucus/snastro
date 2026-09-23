package snastro.parlanti.dominio

/**
 * The embedding of one `Voce` (biometric, ADR 0009). Equal by content (CR-5). Immutable: the array
 * is copied on the way in and [valori] returns a fresh copy, so no caller can alter the stored
 * values. [toString] never prints the values (RC-6).
 */
public class Impronta(valori: FloatArray) {
    private val interni: FloatArray = valori.copyOf()

    /** A copy of the values: mutating it does not change this Impronta. */
    public val valori: FloatArray get() = interni.copyOf()

    override fun equals(other: Any?): Boolean = other is Impronta && other.interni.contentEquals(interni)

    override fun hashCode(): Int = interni.contentHashCode()

    override fun toString(): String = "Impronta(${interni.size} valori)"
}
