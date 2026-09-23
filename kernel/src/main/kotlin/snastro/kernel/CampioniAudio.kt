package snastro.kernel

/**
 * 16 kHz mono float PCM samples. Equal by content (CR-5: an array is never a `data class`
 * property). The array is not copied (it may hold a whole `Registrazione`): treat it as read-only.
 */
public class CampioniAudio(public val campioni: FloatArray) {
    override fun equals(other: Any?): Boolean = other is CampioniAudio && other.campioni.contentEquals(campioni)

    override fun hashCode(): Int = campioni.contentHashCode()

    override fun toString(): String = "CampioniAudio(${campioni.size} campioni)"
}
