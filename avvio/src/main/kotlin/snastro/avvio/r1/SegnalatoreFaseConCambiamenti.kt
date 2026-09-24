package snastro.avvio.r1

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.SegnalatoreFase

/**
 * The pipeline's [SegnalatoreFase] (AC-353/354): writes into [fasi] — the ONE [FasiInCorso] of the
 * open project, the very instance `StatiElaborazione` reads — and then calls [cambiata] for the
 * Registrazione, so every phase change (and the end of the run) reaches S2 as a `Cambiamento`.
 * Never throws (the port's contract): [cambiata] is a non-blocking emit.
 */
internal class SegnalatoreFaseConCambiamenti(
    private val fasi: FasiInCorso,
    private val cambiata: (RegistrazioneId) -> Unit,
) : SegnalatoreFase {
    override fun fase(id: RegistrazioneId, f: FaseElaborazione) {
        fasi.fase(id, f)
        cambiata(id)
    }

    override fun terminata(id: RegistrazioneId) {
        fasi.terminata(id)
        cambiata(id)
    }
}
