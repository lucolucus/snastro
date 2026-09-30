package snastro.sbobinatura.applicazione.porte

import snastro.kernel.ErroreDominio

/**
 * The application/technical failures of the Sbobinatura context — ONE hierarchy per module (ADR 0003
 * amended). `Sbobinatura` has no domain rules of its own (no `:sbobinatura:dominio`, no aggregate):
 * every failure here is an I/O fault of [ScrittoreSbobinatura] surfaced to the caller (AC-157), which
 * an after-commit subscriber retries until it succeeds (ADR 0012).
 */
public sealed interface ErroreApplicazioneSbobinatura : ErroreDominio {
    /** Writing or removing [nomeFile] under `sbobinature/` failed; nothing to do but retry. */
    public data class ScritturaFallita(val nomeFile: String, val messaggio: String) : ErroreApplicazioneSbobinatura
}
