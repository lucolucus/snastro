package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId

/**
 * Published Language of the domain event `SegmentoConfermato` (boundary `eventi-revisione`, ADR 0019 §3): the
 * [confermato] flag of [segmentoId] changed. After-commit subscribers only (view refresh); no synchronous one.
 */
public data class SegmentoConfermato(
    val registrazioneId: RegistrazioneId,
    val segmentoId: SegmentoId,
    val confermato: Boolean,
) : EventoPubblicato
