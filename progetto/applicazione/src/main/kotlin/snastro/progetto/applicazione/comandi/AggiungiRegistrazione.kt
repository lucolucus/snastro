package snastro.progetto.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId

/**
 * Command (ADR 0033 §2): imports [file] (>= 1, in the order the user selected them) into the open Progetto, all or
 * nothing, to [destinazione].
 */
public data class AggiungiRegistrazione(
    public val progettoId: ProgettoId,
    public val file: List<String>,
    public val destinazione: Destinazione,
)

/** Where the files of an [AggiungiRegistrazione] go. */
public sealed interface Destinazione {
    /** Every file is a Parte of ONE new Incontro. */
    public data object NuovoIncontro : Destinazione

    /** One new one-Parte Incontro per file ("N incontri separati"). */
    public data object IncontriSeparati : Destinazione

    /** Every file is a Parte of the existing Incontro [incontroId] of the same Progetto ("Aggiungi parti..."). */
    public data class Incontro(public val incontroId: IncontroId) : Destinazione
}
