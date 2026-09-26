package snastro.sintesi.applicazione.letture

/** AC-S103/S104: a PuntoChiave, with its (possibly unattributed) speaker. */
public data class PuntoChiaveVista(
    val testo: TestoConVociVista,
    val fonti: List<FonteVista>,
    val parlante: VoceVista?,
)
