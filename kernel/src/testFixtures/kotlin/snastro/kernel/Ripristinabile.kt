package snastro.kernel

/**
 * In-memory state (typically a fake repository) that takes part in [UnitaDiLavoroFinta]'s
 * transactions: [istantanea] captures the state and returns the action that restores it on rollback.
 */
public fun interface Ripristinabile {
    public fun istantanea(): () -> Unit
}
