package snastro.sintesi.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.IncontroId

/** Expected failures of the Sintesi context (ADR 0003): the whole hierarchy lives here, one file (CR-8). */
public sealed interface ErroreSintesi : ErroreDominio {
    /** INV-S2: the Incontro already has a Riassunto `in_attesa | in_corso`. */
    public data class RiassuntoGiaAperto(val incontroId: IncontroId) : ErroreSintesi

    /** INV-S6: the local LLM model is not installed. */
    public data object ModelloNonInstallato : ErroreSintesi

    /** INV-S6: the Incontro has no Trascritto yet. */
    public data class TrascrittoNonDisponibile(val incontroId: IncontroId) : ErroreSintesi

    /** INV-S6: an Elaborazione of a Parte of the Incontro is `in_attesa | in_corso`. */
    public data class ElaborazioneGiaAperta(val incontroId: IncontroId) : ErroreSintesi

    /** INV-S6: the estimated input ([LimiteIngresso]) exceeds the limit. */
    public data class RegistrazioneTroppoLunga(val stimaToken: Int, val limite: Int) : ErroreSintesi

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
