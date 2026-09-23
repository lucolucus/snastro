package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Documento reads a Trascritto from Trascrizione,
 * with the titolo and the dataRegistrazione of its Registrazione from Progetto (boundary
 * `trascritto-per-documento`, Customer/Supplier, Published Language only).
 */
public interface LettoreTrascritto {
    /**
     * The Trascritto of [id], or `null` when the Registrazione has none: unknown id, Elaborazione
     * not completata (never started, still running, or fallita). [TrascrittoTesto.segmenti] are
     * ordered as [SegmentoVista] states.
     */
    public fun trascritto(id: RegistrazioneId): TrascrittoTesto?

    /**
     * Every Registrazione whose Elaborazione is completata (so it has a Trascritto), each once.
     * No order is guaranteed.
     */
    public fun registrazioniConTrascritto(): List<RegistrazioneId>
}
