package snastro.documento.applicazione.porte

/**
 * Consumer-owned, write-only port onto the project's `documenti/` folder (boundary
 * `tec-scrittore-documento`, ADR 0010). The `Documento` is written, never read back ([INV-23]):
 * this port deliberately offers no read.
 *
 * [nomeFile] is the file name minted by the `Documento` (`"<AAAA-MM-DD> <titolo>.md"`), relative to
 * `documenti/`. A failure to write or remove is thrown (the caller maps it to an `Esito`).
 */
public interface ScrittoreDocumento {
    /** Writes [markdown] under [nomeFile], replacing any previous content; idempotent. */
    public fun scrivi(nomeFile: String, markdown: String)

    /** Removes the document [nomeFile]; a no-op when it is absent. */
    public fun rimuovi(nomeFile: String)
}
