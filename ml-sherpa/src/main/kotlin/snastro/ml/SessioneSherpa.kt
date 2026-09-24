package snastro.ml

/**
 * One native session: every sherpa object an adapter creates inside [MotoreSherpa.conSessione] is
 * [registra]ted here and released when the session closes — in reverse order, all of them even if
 * one release fails — so no native handle outlives `conSessione` (AC-244, RC-5).
 */
class SessioneSherpa internal constructor(val config: ConfigSessione) : AutoCloseable {
    private val risorse = mutableListOf<AutoCloseable>()
    private var chiusa = false

    /** Keeps [risorsa] until the session closes, then calls [rilascia] on it; returns [risorsa]. */
    fun <R : Any> registra(risorsa: R, rilascia: (R) -> Unit): R {
        check(!chiusa) { "SessioneSherpa is closed: a native object cannot outlive conSessione" }
        risorse += AutoCloseable { rilascia(risorsa) }
        return risorsa
    }

    override fun close() {
        if (chiusa) return
        chiusa = true
        chiudiDa(0)
        risorse.clear()
    }

    // `use` nesting: the last registered is closed first, a failing release keeps the others going and
    // the later failures are attached as suppressed exceptions.
    private fun chiudiDa(indice: Int) {
        if (indice < risorse.size) risorse[indice].use { chiudiDa(indice + 1) }
    }
}
