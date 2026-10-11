package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Where a pipeline port fault goes before the Elaborazione fails with its fixed `motivo` (ADR 0042, CR-7): the
 * user sees only the `motivo` (ADR 0003), the cause reaches the app's log through `:avvio`. Contract
 * (`SegnalatoreGuastiContratto`): it never throws — a throwing implementation is a caller bug.
 */
public fun interface SegnalatoreGuasti {
    /** The Elaborazione of [id] failed with [motivo] because a port threw [causa]. */
    public fun guasto(id: RegistrazioneId, motivo: String, causa: Exception)
}
