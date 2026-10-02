package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId

/**
 * Consumer-owned, read-only port through which Parlanti reads the Voci of an Incontro from Trascrizione
 * (boundary `voci-per-parlanti`, Customer/Supplier, Published Language only; ADR 0033 §4, ADR 0035 §6).
 * The order of the Parti is NOT this port's: it belongs to Progetto and is read through [LettoreRegistrazione.parti].
 */
public interface LettoreVoci {
    /**
     * The CURRENT Voci of the Incontro [incontroId] (after every Revisione so far), once each, ordered by voceId, each
     * with its intervalli in every Parte where it speaks ([VoceVista]); or `null` iff no Parte of the Incontro has a
     * Trascritto (INV-5): unknown Incontro, or no Elaborazione completata in any of its Parti.
     */
    public fun voci(incontroId: IncontroId): List<VoceVista>?

    /**
     * Every CURRENT Segmento of every transcribed Parte of [incontroId], once each: the Segmenti of one Parte are
     * contiguous and ordered by (inizioMs, segmentoId); the order between Parti is unspecified. `null` exactly as
     * [voci]. NEVER the text (ADR 0019 §4.1, consumer `piano-riassegnazione`).
     */
    public fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce>?
}
