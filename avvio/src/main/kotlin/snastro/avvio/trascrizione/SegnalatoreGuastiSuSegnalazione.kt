package snastro.avvio.trascrizione

import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.porte.SegnalatoreGuasti

/**
 * ADR 0042 §4: a transcription phase fault goes to the app's log through [segnalazione] (`segnalazioneApp`, at
 * WARNING since it carries the cause), one line naming the Registrazione and the `motivo` the Elaborazione failed
 * with. The port's contract: it never throws.
 */
internal fun segnalatoreGuastiSu(segnalazione: Segnalazione): SegnalatoreGuasti =
    SegnalatoreGuasti { id, motivo, causa ->
        segnalazione.segnala("Elaborazione di ${id.valore} fallita ($motivo)", causa)
    }
