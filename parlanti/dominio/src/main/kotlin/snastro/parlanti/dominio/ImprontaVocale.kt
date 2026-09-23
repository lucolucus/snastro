package snastro.parlanti.dominio

import snastro.kernel.VoceRef

/** One print, kept individually (never averaged) — owned by a [Parlante], keyed by [voceRef] ([INV-14]). */
public data class ImprontaVocale(val voceRef: VoceRef, val impronta: Impronta)
