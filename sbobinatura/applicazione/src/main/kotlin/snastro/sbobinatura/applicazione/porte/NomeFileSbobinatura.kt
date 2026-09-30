package snastro.sbobinatura.applicazione.porte

/**
 * The [ScrittoreSbobinatura] precondition on `nomeFile`: non-blank, a single path segment (no `/`,
 * no `\`, no NUL), ending in `.md`. Throws [IllegalArgumentException] otherwise. Every
 * implementation calls it before touching anything.
 *
 * `..` cannot climb out of `sbobinature/`: without a separator it is not a segment of its own, and
 * `.`/`..` do not end in `.md`. So a title with an ellipsis (`"Riunione... finale.md"`) stays valid.
 */
public fun richiediNomeFileSbobinatura(nomeFile: String) {
    require(
        nomeFile.isNotBlank() &&
            nomeFile.none { it == '/' || it == '\\' || it == '\u0000' } &&
            nomeFile.endsWith(".md"),
    ) { "nomeFile non valido per sbobinature/: '$nomeFile'" }
}
