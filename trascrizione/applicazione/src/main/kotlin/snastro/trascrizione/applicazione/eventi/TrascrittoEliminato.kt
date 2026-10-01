package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * Published Language (boundary `eventi-trascrizione-incontro`, ADR 0035 §5, ADR 0038): the Trascritto of the Parte
 * [registrazioneId] of the Incontro [incontroId] left with its deleted Parte; [vociRimosse] are the Voci that ceased
 * with it. Published inside the deleting unit by Trascrizione's elimination policy, only if the Parte had a
 * Trascritto; synchronous consumer Parlanti (nested, depth-first).
 */
public data class TrascrittoEliminato(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val vociRimosse: Set<VoceId>,
) : EventoPubblicato
