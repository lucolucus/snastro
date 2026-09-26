package snastro.sintesi.applicazione.letture

/** AC-S103..S105: the last `pronto` Riassunto of the Registrazione, as the tab renders it. */
public data class RiassuntoMostrato(
    val argomento: String?,
    val lunghezzaMassimaParole: Int,
    val superato: Boolean,
    val omessi: Int,
    val sommario: TestoConVociVista?,
    val decisioni: List<ElementoVista>,
    val azioni: List<AzioneVista>,
    val questioniAperte: List<ElementoVista>,
    val puntiChiave: List<PuntoChiaveVista>,
)
