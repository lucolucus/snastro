package snastro.trascrizione.applicazione.letture

import snastro.kernel.VoceId

/** Read-model `VociIncontro` (S2 counts, "Unisci con"): every Voce of an Incontro, ascending by voceId. */
public data class VociIncontro(val voci: List<VoceIncontroRiga>, val numVoci: Int)

/** One Voce of the Incontro with the Parte numbers it speaks in, ascending. */
public data class VoceIncontroRiga(val voceId: VoceId, val etichetta: String, val parti: List<Int>)
