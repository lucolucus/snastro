package snastro.documento.applicazione.letture

/**
 * The Documento's rendered content — view_shape of the `documento` read-model block. [markdown] is
 * the full `.md` content ([Documento.proietta]); [nomeFile] is its file name under `documenti/`
 * ([Documento.nomeFile]). Both are produced by pure functions of their inputs ([INV-23]).
 */
public data class DocumentoVista(
    val markdown: String,
    val nomeFile: String,
)
