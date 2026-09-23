package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Documento reads a Trascritto from Trascrizione,
 * with the titolo and the dataRegistrazione of its Registrazione from Progetto (boundary
 * `trascritto-per-documento`, Customer/Supplier, Published Language only).
 */
public interface LettoreTrascritto {
    /**
     * The Trascritto of [id], or `null` when no Elaborazione of it has ever completed (so no Trascritto
     * exists): unknown id, or every Elaborazione never started, still running or fallita. A fallita
     * followed by a completata yields the Trascritto. [TrascrittoTesto.segmenti] are ordered as
     * [SegmentoVista] states.
     */
    public fun trascritto(id: RegistrazioneId): TrascrittoTesto?

    /**
     * Every Registrazione with a completata Elaborazione (so [trascritto] of it is non-null), each once.
     * No order is guaranteed.
     */
    public fun registrazioniConTrascritto(): List<RegistrazioneId>
}
