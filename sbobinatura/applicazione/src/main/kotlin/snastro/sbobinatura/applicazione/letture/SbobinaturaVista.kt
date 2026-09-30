package snastro.sbobinatura.applicazione.letture

/**
 * The Sbobinatura's rendered content — view_shape of the `sbobinatura` read-model block. [markdown] is
 * the full `.md` content ([Sbobinatura.proietta]); [nomeFile] is its file name under `sbobinature/`
 * ([Sbobinatura.nomeFile]). Both are produced by pure functions of their inputs ([INV-23]).
 */
public data class SbobinaturaVista(
    val markdown: String,
    val nomeFile: String,
)
