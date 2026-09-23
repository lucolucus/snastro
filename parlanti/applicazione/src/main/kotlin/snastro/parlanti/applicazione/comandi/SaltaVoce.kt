package snastro.parlanti.applicazione.comandi

import snastro.kernel.VoceRef

/**
 * Command: skips [voceRef], CONFIRMING it as a new occasionale 'Ospite del <DataRegistrazione>'
 * Parlante ([INV-19], AC-88/AC-89).
 */
public data class SaltaVoce(val voceRef: VoceRef)
