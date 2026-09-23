package snastro.kernel

/** Transactional effect store for the kernel's own contract subclasses. */
class EffettiInMemoria : Ripristinabile {
    private val effetti = mutableSetOf<String>()

    fun scrivi(effetto: String) {
        effetti += effetto
    }

    fun visibili(): Set<String> = effetti.toSet()

    override fun istantanea(): () -> Unit {
        val copia = effetti.toSet()
        return {
            effetti.clear()
            effetti += copia
        }
    }
}
