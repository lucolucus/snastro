package snastro.ui.registrazione

import snastro.kernel.VoceId

/**
 * The steps of "Dai un nome a questa frase" (ADR 0019 §5), decided by the S3 presenter ([passiNominaFrase])
 * and run by `ComandiVoce.nominaFrase` as SEPARATE commands (not one transaction).
 */
sealed interface PassiNominaFrase {
    /** (a) The Segmento is already on a Voce of P: `ConfermaSegmento(true)`. */
    data object SoloConferma : PassiNominaFrase

    /** (b) The Segmento is alone in [voceId]: `ConfermaAttribuzione`, then `ConfermaSegmento(true)`. */
    data class AttribuisciVoce(val voceId: VoceId, val obiettivo: ObiettivoNome) : PassiNominaFrase

    /** (c) P already has a Voce here: `RiassegnaSegmento(seg, [voceId])` (a manual move confirms). */
    data class Sposta(val voceId: VoceId) : PassiNominaFrase

    /** (d) Otherwise: `RiassegnaSegmento(seg, null)`, then `ConfermaAttribuzione(new Voce, obiettivo)`. */
    data class NuovaVoce(val obiettivo: ObiettivoNome) : PassiNominaFrase
}
