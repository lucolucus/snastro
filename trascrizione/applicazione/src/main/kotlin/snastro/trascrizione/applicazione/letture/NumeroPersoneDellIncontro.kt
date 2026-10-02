package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione

/**
 * Read-model (ADR 0039, AC-I34): the value every 'Numero di persone' field of an Incontro is prefilled with — the
 * `numeroPersone` of its latest Elaborazione by `(creataAlle, id)` over all its Parti, derived, never stored apart.
 * Read-only; no rule lives here.
 */
public class NumeroPersoneDellIncontro(
    private val registrazioni: LettoreRegistrazione,
    private val elaborazioni: ElaborazioneRepository,
) {
    /** `null` for an unknown Incontro, one without Elaborazioni, or when the latest had no number. */
    public fun numeroPersonePrecompilato(incontroId: IncontroId): Int? =
        registrazioni.parti(incontroId).orEmpty()
            .flatMap { elaborazioni.diRegistrazione(it.registrazioneId) }
            .maxWithOrNull(compareBy({ it.creataAlle }, { it.id.valore }))
            ?.numeroPersone?.valore
}
