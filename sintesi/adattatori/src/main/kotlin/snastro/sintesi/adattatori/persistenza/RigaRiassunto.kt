package snastro.sintesi.adattatori.persistenza

/** One `riassunto` row as the transition queries project it: the Parte's `registrazione_id` joined in by SQL. */
internal data class RigaRiassunto(
    val id: String,
    val registrazioneId: String,
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
    registrazioneId: String,
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
    id, registrazioneId, stato, argomento, lunghezzaMassimaParole, richiestoAlle, avviatoAlle,
    motivoFallimento, sommario, omessi, struttura,
)
