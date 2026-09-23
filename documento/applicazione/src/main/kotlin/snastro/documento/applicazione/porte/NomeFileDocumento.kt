package snastro.documento.applicazione.porte

/**
 * The [ScrittoreDocumento] precondition on `nomeFile`: non-blank, a single path segment (no `/`,
 * no `\`, no NUL), ending in `.md`. Throws [IllegalArgumentException] otherwise. Every
 * implementation calls it before touching anything.
 *
 * `..` cannot climb out of `documenti/`: without a separator it is not a segment of its own, and
 * `.`/`..` do not end in `.md`. So a title with an ellipsis (`"Riunione... finale.md"`) stays valid.
 */
public fun richiediNomeFileDocumento(nomeFile: String) {
    require(
        nomeFile.isNotBlank() &&
            nomeFile.none { it == '/' || it == '\\' || it == '\u0000' } &&
            nomeFile.endsWith(".md"),
    ) { "nomeFile non valido per documenti/: '$nomeFile'" }
}
