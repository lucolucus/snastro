// Named per dev-architecture-app.md#pacchetti (an aggregate's domain events in <Aggregato>Eventi.kt).
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.parlanti.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef

/** The Voce [voceRef] is attributed to [parlanteId]; [precedente] is the Parlante it had before, if any. */
public data class AttribuzioneConfermata(
    val voceRef: VoceRef,
    val parlanteId: ParlanteId,
    val precedente: ParlanteId?,
) : EventoDominio
