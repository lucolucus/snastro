package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * Published Language (boundary `eventi-trascrizione-incontro`, ADR 0018, ADR 0035 §5): the completion transaction
 * REPLACED the Trascritto of the Parte [registrazioneId] of the Incontro [incontroId]; [vociRimosse] are the Voci that
 * ceased with the old Segmenti (they spoke in no other Parte). Published only then, BEFORE `ElaborazioneCompletata`, in
 * that same transaction: its synchronous subscriber (Parlanti only, never Sintesi) runs before the COMMIT.
 */
public data class TrascrittoSostituito(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val vociRimosse: Set<VoceId>,
) : EventoPubblicato
