package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Sintesi reads a Trascritto and the state of its latest
 * Elaborazione from Trascrizione (boundary `trascritto-per-sintesi`, ADR 0021 §3, Published Language only).
 * Same name as Documento's port, another package: each consumer owns its own need.
 */
public interface LettoreTrascritto {
    /**
     * The Segmenti of [r]'s Trascritto, or `null` when [r] has no Trascritto (never transcribed, or its
     * first Elaborazione still in_attesa / in_corso / fallita). Ordered as the supplier's
     * `VociDelTrascritto.segmenti` (INV-7: by segmentoId), each with its CURRENT voceId after any
     * Revisione. While a re-run is queued or running, the OLD Trascritto is returned.
     */
    public fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>?

    /** `true` iff the latest Elaborazione of [r] is in_attesa or in_corso. */
    public fun elaborazioneAperta(r: RegistrazioneId): Boolean
}
