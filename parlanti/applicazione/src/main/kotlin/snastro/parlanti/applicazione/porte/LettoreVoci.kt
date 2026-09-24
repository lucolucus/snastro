package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Parlanti reads the Voci of a Trascritto from
 * Trascrizione (boundary `voci-per-parlanti`, Customer/Supplier, Published Language only).
 */
public interface LettoreVoci {
    /**
     * The CURRENT Voci of the Trascritto of [id] (after every Revisione so far), ordered by voceId; or
     * `null` iff the Registrazione has no Trascritto (INV-5): unknown id, or no Elaborazione completata.
     */
    public fun voci(id: RegistrazioneId): List<VoceVista>?

    /**
     * Every CURRENT Segmento of the Trascritto of [id], once each, ordered by (inizioMs, segmentoId); or
     * `null` iff the Registrazione has no Trascritto (INV-5), exactly as [voci]. NEVER the text
     * (ADR 0019 §4.1, consumer `piano-riassegnazione`).
     */
    public fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce>?
}
