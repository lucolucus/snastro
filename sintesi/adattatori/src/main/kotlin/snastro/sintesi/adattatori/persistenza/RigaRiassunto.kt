package snastro.sintesi.adattatori.persistenza

/** One `riassunto` row as its queries project it (`SELECT *`, keyed by `incontro_id`). */
internal data class RigaRiassunto(
    val id: String,
    val incontroId: String,
    val stato: String,
    val argomento: String?,
    val lunghezzaMassimaParole: Long,
    val richiestoAlle: Long,
    val avviatoAlle: Long?,
    val motivoFallimento: String?,
    val sommario: String?,
    val omessi: Long?,
    val struttura: String?,
)

@Suppress("LongParameterList") // the mapper SQLDelight's generated queries ask for: one parameter per column
internal fun rigaRiassunto(
    id: String,
    incontroId: String,
    stato: String,
    argomento: String?,
    lunghezzaMassimaParole: Long,
    richiestoAlle: Long,
    avviatoAlle: Long?,
    motivoFallimento: String?,
    sommario: String?,
    omessi: Long?,
    struttura: String?,
): RigaRiassunto = RigaRiassunto(
    id, incontroId, stato, argomento, lunghezzaMassimaParole, richiestoAlle, avviatoAlle,
    motivoFallimento, sommario, omessi, struttura,
)
