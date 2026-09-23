package snastro.trascrizione.dominio

import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDominio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** Expected rule violations of the Trascrizione context (ADR 0003). */
public sealed interface ErroreTrascrizione : ErroreDominio {
    /** INV-3: [Elaborazione] cannot move from [da] to [verso]; its state is unchanged. */
    public data class TransizioneNonAmmessa(
        val id: ElaborazioneId,
        val da: StatoElaborazione,
        val verso: StatoElaborazione,
    ) : ErroreTrascrizione

    /** INV-4 (ADR 0007, `elaborazione_aperta_unica`): the Registrazione already has an open Elaborazione. */
    public data class ElaborazioneGiaAperta(val registrazioneId: RegistrazioneId) : ErroreTrascrizione

    /** INV-4 (ADR 0007, `elaborazione_completata_unica`): the Registrazione already has a `completata` one. */
    public data class ElaborazioneGiaCompletata(val registrazioneId: RegistrazioneId) : ErroreTrascrizione

    /** AC-21: the pipeline produced no Segmento, so no [Trascritto] can exist. */
    public data object NessunParlatoRilevato : ErroreTrascrizione

    /** INV-7: a Segmento must end within the duration of the Registrazione. */
    public data class SegmentoOltreLaDurata(val intervallo: IntervalloMs, val durataMs: Long) : ErroreTrascrizione

    /** INV-9/10/11: the Voce is not (or no longer) a Voce of this [Trascritto]. */
    public data class VoceNonTrovata(val voceId: VoceId) : ErroreTrascrizione

    /** INV-11: the Segmento is not a Segmento of this [Trascritto]. */
    public data class SegmentoNonTrovato(val segmentoId: SegmentoId) : ErroreTrascrizione

    /** INV-9: a Voce cannot be joined with itself. */
    public data class UnioneNonAmmessa(val sopravvive: VoceId, val rimossa: VoceId) : ErroreTrascrizione

    /** INV-10: the Segmenti to split off must be a non-empty proper subset of [origine]'s Segmenti. */
    public data class DivisioneNonAmmessa(val origine: VoceId, val segmenti: Set<SegmentoId>) : ErroreTrascrizione

    /**
     * INV-11: the Segmento is already on [destinazione], or [destinazione] is `null` (a new Voce) while the
     * Segmento is the only one of its Voce — a move that changes no grouping yet would remove the Voce.
     */
    public data class RiassegnazioneNonAmmessa(
        val segmentoId: SegmentoId,
        val destinazione: VoceId?,
    ) : ErroreTrascrizione
}
