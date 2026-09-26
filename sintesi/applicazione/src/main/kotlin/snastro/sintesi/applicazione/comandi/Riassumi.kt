package snastro.sintesi.applicazione.comandi

import snastro.kernel.RegistrazioneId

/**
 * Command: queues a Riassunto for [registrazioneId], optionally about [argomento] (AC-S77..S82, ADR 0021 §3).
 * A blank [argomento] is stored as absent; the previous Riassunto's Argomento is never inherited (AC-S79 —
 * only the UI prefills the field it shows the user).
 */
public data class Riassumi(public val registrazioneId: RegistrazioneId, public val argomento: String? = null)
