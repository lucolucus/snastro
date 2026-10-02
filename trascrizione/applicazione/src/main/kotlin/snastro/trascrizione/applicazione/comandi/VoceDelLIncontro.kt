package snastro.trascrizione.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.trascrizione.dominio.ErroreTrascrizione.SegmentoNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.VoceNonTrovata
import snastro.trascrizione.dominio.VociDellIncontro

/**
 * INV-I7: what a command names must belong to THIS root's Incontro. The root only sees ids, so a `VoceRef` of another
 * Incontro is refused here: when [incontroDelleVoci] is stated and is not the root's Incontro, the command is refused
 * BEFORE the root is touched — whatever the Voci are, even all `null` — and nothing changes.
 */
internal fun VociDellIncontro.altroIncontro(incontroDelleVoci: IncontroId?): Boolean =
    incontroDelleVoci != null && incontroDelleVoci != incontroId

/** A command naming a Segmento: its Segmento is not in this Incontro, `SegmentoNonTrovato` of the Parte's ref. */
internal fun VociDellIncontro.segmentoDiQuestoIncontro(
    incontroDelleVoci: IncontroId?,
    segmento: SegmentoRef,
): Esito.Errore? =
    if (altroIncontro(incontroDelleVoci)) Esito.Errore(SegmentoNonTrovato(segmento)) else null

/** A command naming only Voci: `VoceNonTrovata` of the first one. */
internal fun VociDellIncontro.vociDiQuestoIncontro(
    incontroDelleVoci: IncontroId?,
    prima: VoceId,
): Esito.Errore? =
    if (altroIncontro(incontroDelleVoci)) Esito.Errore(VoceNonTrovata(prima)) else null
