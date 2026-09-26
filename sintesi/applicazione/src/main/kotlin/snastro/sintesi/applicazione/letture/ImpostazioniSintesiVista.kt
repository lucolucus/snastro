package snastro.sintesi.applicazione.letture

/**
 * Read-model `vista-impostazioni-sintesi` (AC-S109, boundary owned here): the per-Progetto lunghezza
 * massima del Riassunto, with its bounds, for the inline editor of the Riassunto tab. [minimo] and
 * [massimo] never depend on the Progetto: they are [snastro.sintesi.dominio.LunghezzaMassimaParole]'s
 * own constants, shown so the editor can validate before submitting.
 */
public data class ImpostazioniSintesiVista(
    val lunghezzaMassimaParole: Int,
    val minimo: Int,
    val massimo: Int,
)
