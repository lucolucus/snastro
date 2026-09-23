package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * The Trascritto of one Registrazione as Documento renders it (pinned view of
 * `trascritto-per-documento`): [titolo] and [dataRegistrazione] of the Registrazione, then every
 * Segmento, ordered by `intervallo.inizioMs` and then `segmentoId` across the Voci.
 */
public data class TrascrittoTesto(
    val registrazioneId: RegistrazioneId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val segmenti: List<SegmentoVista>,
)
