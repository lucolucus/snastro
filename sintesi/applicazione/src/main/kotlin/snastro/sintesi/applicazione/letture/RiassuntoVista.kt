package snastro.sintesi.applicazione.letture

import snastro.kernel.IncontroId

/**
 * Read-model `vista-riassunto` (AC-S102..S108, boundary owned here): the Riassunto tab's whole data
 * view for one Registrazione, built by [RiassuntoVisteLettura]. The read-model itself is `null` (not
 * this type) when the Registrazione has no Trascritto: the tab is not offered.
 */
public data class RiassuntoVista(
    val incontroId: IncontroId,
    val modello: StatoModelloVista,
    val richiestaAperta: RichiestaApertaVista?,
    val ultimoFallimento: FallimentoVista?,
    val disponibilita: DisponibilitaVista,
    val argomentoPrecompilato: String?,
    val mostrato: RiassuntoMostrato?,
)
