package snastro.sintesi.applicazione.letture

/** AC-S103/S104: an Azione, with its (possibly unattributed) Responsabile. */
public data class AzioneVista(
    val testo: TestoConVociVista,
    val fonti: List<FonteVista>,
    val responsabile: VoceVista?,
)
