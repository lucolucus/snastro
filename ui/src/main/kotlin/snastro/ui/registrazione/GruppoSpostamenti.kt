package snastro.ui.registrazione

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * One preview line "<da> → <a>: <frasi>" (ADR 0019 Amendment (b).2): pure presentation grouping of the plan.
 * [perParte] (AC-I79) splits [frasi] by Parte, in plan order; shown only over a multi-Parte Incontro.
 */
data class GruppoSpostamenti(
    val da: VoceId,
    val a: VoceId,
    val frasi: Int,
    val perParte: List<FrasiInParte> = emptyList(),
)

/** [frasi] moved Segmenti of the Parte [registrazioneId]. */
data class FrasiInParte(val registrazioneId: RegistrazioneId, val frasi: Int)
