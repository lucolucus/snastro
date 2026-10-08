package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import java.time.Instant

/**
 * The `schermata-progetti` (S1) data view of one known project (AC-159). Own type — never the
 * porte-progetto [snastro.progetto.applicazione.porte.VoceRegistro] it is projected from: this
 * block's shape is the UI's Published Language contract, independent of the registry's own.
 * [cartellaTrovata] is false when the project's folder is no longer at [percorso] (R0: "Cartella non trovata").
 */
public data class ProgettoVista(
    val progettoId: ProgettoId,
    val nome: String,
    val percorso: String,
    val numRegistrazioni: Int,
    val ultimaAttivita: Instant,
    val cartellaTrovata: Boolean,
)
