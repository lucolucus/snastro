package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * Read-model `trascritto-view` (S3, `TrascrittoQuery.vista`): the Trascrizione slice of the
 * `schermata-registrazione` screen's data. Parlante attribution (`nome`, `tipoParlante`, `parlanteId`)
 * is joined in by the presenter from `identificazione-voci`, never here (R1 split, ux-proposal).
 */
public data class TrascrittoView(
    val registrazioneId: RegistrazioneId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
    val segmenti: List<SegmentoTrascrittoView>,
    val voci: List<VoceTrascrittoView>,
)
