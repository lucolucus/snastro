package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef

/**
 * Metadata of ONE stored print row (boundary `repo-parlanti`, ADR 0012 Amendment (b)) — never the
 * embedding: which Parlante, which Voce, the `SorgenteImpronta.chiave` and the model id it was extracted with.
 */
public data class RigaImpronta(
    val parlanteId: ParlanteId,
    val voceRef: VoceRef,
    val sorgente: String,
    val modello: String,
)
