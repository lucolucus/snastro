package snastro.sintesi.applicazione.letture

import snastro.kernel.IncontroId

/**
 * Read-model `vista-riassunto` (AC-S102..S108, boundary owned here): the Riassunto tab's whole data view for one
 * Incontro (ADR 0037 §6), built by [RiassuntoVisteLettura]. [numParti] is the number of Parti it has now. The
 * read-model itself is `null` (not this type) when the Incontro is unknown, or is a one-Parte Incontro whose Parte
 * has no Trascritto (the tab is not offered, as for a Registrazione, INV-I3).
 */
public data class RiassuntoVista(
    val incontroId: IncontroId,
    val numParti: Int,
    val modello: StatoModelloVista,
    val richiestaAperta: RichiestaApertaVista?,
    val ultimoFallimento: FallimentoVista?,
    val disponibilita: DisponibilitaVista,
    val argomentoPrecompilato: String?,
    val mostrato: RiassuntoMostrato?,
)
