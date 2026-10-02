package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.sintesi.dominio.StatoParte

/**
 * The state of a Parte as Sintesi's [LettoreTrascritto] reports it (ADR 0033 §4): the domain [StatoParte] that
 * `Riassumibilita` reads, named for the port; one enum, no mapping table.
 */
public typealias StatoParteSintesi = StatoParte

/**
 * Consumer-owned, read-only port through which Sintesi reads, per Parte, a Trascritto and the state of its
 * Elaborazioni from Trascrizione (boundary `porte-sintesi`, ADR 0033 §4, ADR 0021 §3, Published Language only).
 * Same name as Sbobinatura's port, another package: each consumer owns its own need.
 */
public interface LettoreTrascritto {
    /**
     * The Segmenti of [r]'s Trascritto, or `null` when [r] has no Trascritto (never transcribed, or its
     * first Elaborazione still in_attesa / in_corso / fallita). Ordered as the supplier's
     * `VociDelTrascritto.segmenti` (INV-7: by segmentoId), each with its CURRENT voceId after any
     * Revisione. While a re-run is queued or running, the OLD Trascritto is returned.
     */
    public fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>?

    /**
     * The state of the Parte [r] (D-0020): [StatoParte.IN_TRASCRIZIONE] when its latest Elaborazione is in_attesa or
     * in_corso (a re-run of a transcribed Parte too); otherwise [StatoParte.TRASCRITTA] with a Trascritto,
     * [StatoParte.NON_RIUSCITA] without one when the latest Elaborazione failed, else [StatoParte.DA_TRASCRIVERE]
     * (an unknown [r] included).
     */
    public fun statoParte(r: RegistrazioneId): StatoParteSintesi
}
