package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * The Trascritto of one Registrazione as Sbobinatura renders it (pinned view of
 * `trascritto-per-sbobinatura`): [titolo] and [dataRegistrazione] of the Registrazione, then every
 * Segmento, ordered by `intervallo.inizioMs` and then `segmentoId` across the Voci.
 */
public data class TrascrittoTesto(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val segmenti: List<SegmentoVista>,
)
