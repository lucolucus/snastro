package snastro.trascrizione.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * The `Revisione` events of [VociDellIncontro], keyed by [incontroId] and [SegmentoRef] (ADR 0035 §5). Nested so they
 * live beside the per-Registrazione events of the legacy [Trascritto] root until the sweep retires those.
 */
public sealed interface EventoRevisione : EventoDominio {
    public val incontroId: IncontroId

    /** INV-9: every Segmento of [rimossa], in any Parte, moved to [sopravvissuta]; [rimossa] no longer exists. */
    public data class VociUnite(
        override val incontroId: IncontroId,
        val sopravvissuta: VoceId,
        val rimossa: VoceId,
    ) : EventoRevisione

    /** INV-10: [spostati] (in the new Voce's order) left [origine] and form [nuova]. */
    public data class VoceDivisa(
        override val incontroId: IncontroId,
        val origine: VoceId,
        val nuova: VoceId,
        val spostati: List<SegmentoRef>,
    ) : EventoRevisione

    /** INV-11: [segmento] moved [da] → [a]; [daRimossa] if [da] was emptied, [aNuova] if [a] was created. */
    public data class SegmentoRiassegnato(
        override val incontroId: IncontroId,
        val segmento: SegmentoRef,
        val da: VoceId,
        val a: VoceId,
        val daRimossa: Boolean,
        val aNuova: Boolean,
    ) : EventoRevisione

    /** INV-26: the [confermato] flag of [segmento] changed. */
    public data class SegmentoConfermato(
        override val incontroId: IncontroId,
        val segmento: SegmentoRef,
        val confermato: Boolean,
    ) : EventoRevisione
}
