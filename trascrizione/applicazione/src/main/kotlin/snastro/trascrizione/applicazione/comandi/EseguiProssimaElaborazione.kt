package snastro.trascrizione.applicazione.comandi

import snastro.kernel.ElaborazioneId

/**
 * Command (actor: sistema, R17): runs the oldest `in_attesa` Elaborazione through the local pipeline
 * (AC-68), skipping any id in [esclusi]. No effect if none remains eligible.
 *
 * [esclusi] (AC-313, F-G): the pipeline dispatcher (`:avvio`) grows this set, per session, with the
 * ids of heads whose avvio keeps being refused or keeps escaping — so a stuck head never blocks the
 * rest of the FIFO queue forever; it stays `in_attesa`, just no longer picked, while every other
 * eligible Elaborazione keeps draining normally.
 */
public data class EseguiProssimaElaborazione(public val esclusi: Set<ElaborazioneId> = emptySet())
