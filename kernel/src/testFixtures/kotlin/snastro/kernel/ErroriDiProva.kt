package snastro.kernel

/** Error hierarchy for kernel-level tests and contracts (CR-8 shape: sealed `Errore<X> : ErroreDominio`). */
public sealed interface ErroreDiProva : ErroreDominio {
    public data class Fallito(val motivo: String) : ErroreDiProva
}
