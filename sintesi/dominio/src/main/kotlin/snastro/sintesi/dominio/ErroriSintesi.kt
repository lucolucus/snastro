package snastro.sintesi.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.IncontroId

/** Expected failures of the Sintesi context (ADR 0003): the whole hierarchy lives here, one file (CR-8). */
public sealed interface ErroreSintesi : ErroreDominio {
    /**
     * INV-S2: the Incontro already has a Riassunto `in_attesa | in_corso`. [incontroId] is set by the repository's
     * backstop; [Riassumibilita] (pinned with no Incontro parameter, ADR 0037 §2) leaves it null.
     */
    public data class RiassuntoGiaAperto(val incontroId: IncontroId? = null) : ErroreSintesi

    /** INV-S6: the local LLM model is not installed. */
    public data object ModelloNonInstallato : ErroreSintesi

    /** INV-I9: Parte number [parte] (the first blocking one) has no Trascritto and no run (`DA_TRASCRIVERE`). */
    public data class PartiNonTrascritte(val parte: Int) : ErroreSintesi

    /** INV-I9: Parte number [parte] (the first blocking one) has an Elaborazione open (`IN_TRASCRIZIONE`). */
    public data class ElaborazioneGiaAperta(val parte: Int) : ErroreSintesi

    /** INV-I9: Parte number [parte] (the first blocking one) has no Trascritto, its last run failed. */
    public data class PartiFallite(val parte: Int) : ErroreSintesi

    /** INV-I9: the estimated input of the whole Incontro ([LimiteIngresso]) exceeds the limit (ADR 0037 §2). */
    public data class IngressoTroppoLungo(val stimaToken: Int, val limite: Int) : ErroreSintesi

    /** AC-S1: the [Argomento] is longer than [Argomento.MASSIMO_CARATTERI]. */
    public data class ArgomentoTroppoLungo(val lunghezza: Int, val massimo: Int) : ErroreSintesi

    /** INV-S9: the lunghezza massima is outside [[minimo], [massimo]] words. */
    public data class LunghezzaMassimaFuoriIntervallo(
        val valore: Int,
        val minimo: Int,
        val massimo: Int,
    ) : ErroreSintesi

    /** INV-S1: the Riassunto cannot move from [da] to [verso] (canonical state codes); nothing changed. */
    public data class TransizioneNonAmmessa(val da: String, val verso: String) : ErroreSintesi

    /** No Riassunto with this id. */
    public data class RiassuntoNonTrovato(val id: String) : ErroreSintesi
}
