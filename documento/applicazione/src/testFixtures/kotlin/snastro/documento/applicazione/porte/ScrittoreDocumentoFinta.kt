package snastro.documento.applicazione.porte

/** In-memory [ScrittoreDocumento]: [documenti] shows what was written, for the consumers' tests. */
public class ScrittoreDocumentoFinta : ScrittoreDocumento {
    private val scritti = mutableMapOf<String, String>()

    /** Snapshot of the documents currently present, name → markdown. */
    public val documenti: Map<String, String> get() = scritti.toMap()

    override fun scrivi(nomeFile: String, markdown: String) {
        scritti[nomeFile] = markdown
    }

    override fun rimuovi(nomeFile: String) {
        scritti.remove(nomeFile)
    }
}
