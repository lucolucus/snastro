package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId

/**
 * 'Trascrivi' on an Incontro (ADR 0039): queues one `Elaborazione` per Parte that has neither a Trascritto nor an
 * open run, all with the same optional [numeroPersone] (1..10; `null` = automatic clustering). See
 * [AvviaElaborazioniDellIncontroServizio].
 */
public data class AvviaElaborazioniDellIncontro(val incontroId: IncontroId, val numeroPersone: Int? = null)
