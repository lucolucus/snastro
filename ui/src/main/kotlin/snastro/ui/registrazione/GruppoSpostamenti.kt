package snastro.ui.registrazione

import snastro.kernel.VoceId

/** One preview line "<da> → <a>: <frasi>" (ADR 0019 Amendment (b).2): pure presentation grouping of the plan. */
data class GruppoSpostamenti(val da: VoceId, val a: VoceId, val frasi: Int)
