package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId

/**
 * Published by `RiallineaImpronte` after the commit of >= 1 refreshed print row of the Incontro
 * (boundary `eventi-parlanti`, ADR 0012 Amendment (b)); no `Sbobinatura` change.
 */
public data class ImpronteRiallineate(val incontroId: IncontroId) : EventoPubblicato
