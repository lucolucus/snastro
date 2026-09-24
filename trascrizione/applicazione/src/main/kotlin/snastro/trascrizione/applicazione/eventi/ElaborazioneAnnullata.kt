package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published Language (boundary `eventi-elaborazione`, ADR 0018 Amendment (b)): `AnnullaElaborazione` deleted a
 * never-started (`in_attesa`) Elaborazione of [registrazioneId]. After-commit subscribers only (view refresh).
 */
public data class ElaborazioneAnnullata(val registrazioneId: RegistrazioneId) : EventoPubblicato
