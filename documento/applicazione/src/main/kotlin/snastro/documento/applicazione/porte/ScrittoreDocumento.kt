package snastro.documento.applicazione.porte

import java.io.IOException

/**
 * Consumer-owned, write-only port onto the project's `documenti/` folder (boundary
 * `tec-scrittore-documento`, ADR 0010). The `Documento` is written, never read back ([INV-23]):
 * this port deliberately offers no read.
 *
 * **nomeFile** is the name minted by the `Documento` (`"<AAAA-MM-DD> <titolo>.md"`), relative to
 * `documenti/`. Precondition (checked by [richiediNomeFileDocumento]): non-blank, one single
 * path segment (no `/`, no `\`, no NUL, so no `..` traversal), ending in `.md`. A violation
 * throws [IllegalArgumentException]: a caller bug, never an expected failure.
 *
 * **Failures:** every I/O fault surfaces as [IOException]. An adapter wraps `UncheckedIOException`
 * and `SecurityException` into one. The caller maps [IOException] to an `Esito` (ADR 0003); it must
 * not catch a generic `Exception` (detekt).
 *
 * **Concurrency:** callers serialize writes to the same `nomeFile`. An adapter still writes through
 * a uniquely named temporary file in `documenti/` and moves it in place with
 * `ATOMIC_MOVE` + `REPLACE_EXISTING`.
 */
public interface ScrittoreDocumento {
    /**
     * Writes [markdown] under [nomeFile], replacing any previous content. The bytes are [markdown]
     * encoded as UTF-8, without a BOM, exactly as given. Writing the same content again leaves the
     * same observable result.
     *
     * Atomic (ADR 0010): if this throws, [nomeFile] holds either its previous content or nothing.
     * It never holds a partial file, and no temporary file is left behind.
     */
    @Throws(IOException::class)
    public fun scrivi(nomeFile: String, markdown: String)

    /**
     * Removes the document [nomeFile]; a no-op when it is absent. Unconditional: a caller that
     * renames a document must not call it when the old name equals the new one.
     */
    @Throws(IOException::class)
    public fun rimuovi(nomeFile: String)
}
