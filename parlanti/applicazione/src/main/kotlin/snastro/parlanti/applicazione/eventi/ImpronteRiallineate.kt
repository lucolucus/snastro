package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published by `RiallineaImpronte` after the commit of >= 1 refreshed print row of the Registrazione
 * (boundary `eventi-parlanti`, ADR 0012 Amendment (b)); no `Documento` change.
 */
public data class ImpronteRiallineate(val registrazioneId: RegistrazioneId) : EventoPubblicato
