package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Progress of the pipeline (boundary `tec-segnalatore-fase`, ADR 0004): fire-and-forget signals that
 * never fail the pipeline. Contract: `SegnalatoreFaseContratto`.
 */
public interface SegnalatoreFase {
    /** The Elaborazione of [id] entered [f]. */
    public fun fase(id: RegistrazioneId, f: FaseElaborazione)

    /** The Elaborazione of [id] ended (completata or fallita): it has no phase any more. */
    public fun terminata(id: RegistrazioneId)
}
