package snastro.documento.applicazione.porte

import java.time.LocalDate

/**
 * Published Language data a [LettoreTrascrittoContratto] seeds as one Registrazione of the Progetto:
 * its [titolo] (the source file name without extension), [dataRegistrazione] and [durataMs]. The id
 * is minted by the supplier (see [AmbienteLettoreTrascritto.aggiungiRegistrazione]).
 */
public data class SemeRegistrazione(
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
