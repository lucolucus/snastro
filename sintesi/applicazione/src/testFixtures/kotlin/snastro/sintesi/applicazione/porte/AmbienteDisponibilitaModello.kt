package snastro.sintesi.applicazione.porte

/**
 * The supplier side of [DisponibilitaModelloLinguisticoContratto]: each subclass drives ITS supplier
 * (the Finta's state in D1; in D2 `:avvio`'s state holder over a fake provisioning and a models
 * directory) through the optional model's life. It starts with nothing on disk and no download.
 */
public interface AmbienteDisponibilitaModello {
    /** The implementation under contract, over the current supplier session. */
    public val disponibilita: DisponibilitaModelloLinguistico

    /** The user starts (or resumes) the download: it is now running. */
    public fun avviaDownload()

    /** The running download receives more bytes (the supplier decides how many, > 0, never past the total). */
    public fun avanza()

    /** The running download completes: verified and installed, with the marker matching the catalogue. */
    public fun completa()

    /**
     * The running download fails with the supplier-side failure that maps to [motivo]
     * (D2: the matching provisioning error, e.g. a hash mismatch for [MotivoDownload.FileNonIntegro]).
     */
    public fun fallisci(motivo: MotivoDownload)

    /** Puts on disk an installed model whose marker does NOT match the catalogue's hash (e.g. an older version). */
    public fun installaConMarcatoreDiverso()

    /** Restarts the supplier over the same disk (the session state is lost); returns the new session's reader. */
    public fun riavvia(): DisponibilitaModelloLinguistico
}
