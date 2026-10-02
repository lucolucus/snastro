package snastro.trascrizione.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.VoceId
import snastro.trascrizione.dominio.ErroreTrascrizione.VoceNonTrovata
import snastro.trascrizione.dominio.VociDellIncontro

/**
 * INV-I7: the Voci a command names must belong to THIS root's Incontro. The root only sees `VoceId`s, so a
 * `VoceRef` of another Incontro is refused here: [incontroDelleVoci] (when stated) must be the root's Incontro, else
 * `VoceNonTrovata` of the first offending Voce and nothing is touched.
 */
internal fun VociDellIncontro.vociDiQuestoIncontro(
    incontroDelleVoci: IncontroId?,
    voci: Iterable<VoceId?>,
): Esito.Errore? =
    if (incontroDelleVoci == null || incontroDelleVoci == incontroId) {
        null
    } else {
        voci.firstNotNullOfOrNull { it }?.let { Esito.Errore(VoceNonTrovata(it)) }
    }
