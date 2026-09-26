package snastro.sintesi.applicazione.porte

/**
 * The model's raw, UNVERIFIED answer: the root applies the Verifica delle fonti ([INV-S4]) to it.
 * Speakers appear in every text only as `{V<n>}`.
 */
public data class RispostaModello(
    val sommario: String?,
    val decisioni: List<ElementoRisposta>,
    val questioniAperte: List<ElementoRisposta>,
    val azioni: List<AzioneRisposta>,
    val puntiChiave: List<PuntoChiaveRisposta>,
)
