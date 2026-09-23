package snastro.documento.applicazione.porte

import snastro.kernel.ErroreDominio

/**
 * The application/technical failures of the Documento context — ONE hierarchy per module (ADR 0003
 * amended). `Documento` has no domain rules of its own (no `:documento:dominio`, no aggregate):
 * every failure here is an I/O fault of [ScrittoreDocumento] surfaced to the caller (AC-157), which
 * an after-commit subscriber retries until it succeeds (ADR 0012).
 */
public sealed interface ErroreApplicazioneDocumento : ErroreDominio {
    /** Writing or removing [nomeFile] under `documenti/` failed; nothing to do but retry. */
    public data class ScritturaFallita(val nomeFile: String, val messaggio: String) : ErroreApplicazioneDocumento
}
