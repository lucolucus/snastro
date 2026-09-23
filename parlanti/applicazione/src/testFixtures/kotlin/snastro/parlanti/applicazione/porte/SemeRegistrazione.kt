package snastro.parlanti.applicazione.porte

import java.time.LocalDate

/**
 * Published Language data a [LettoreRegistrazioneContratto] seeds into the supplier: one source
 * audio file named `<titolo>.<estensione>`, recorded on [dataRegistrazione], lasting [durataMs].
 * The ids are minted by the supplier (see [AmbienteLettoreRegistrazione.semina]).
 */
public data class SemeRegistrazione(
    val titolo: String,
    val estensione: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
