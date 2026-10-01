package snastro.kernel

/**
 * Identity of the `Incontro` (ADR 0033 §1, Published Language): minted through [GeneratoreId] for a new Incontro, by
 * `7.sqm` for a migrated one. Immutable, never reused; no code relies on it being equal to a `RegistrazioneId`.
 */
@JvmInline
public value class IncontroId(public val valore: String) {
    init {
        require(valore.isNotBlank()) { "IncontroId vuoto" }
    }
}
