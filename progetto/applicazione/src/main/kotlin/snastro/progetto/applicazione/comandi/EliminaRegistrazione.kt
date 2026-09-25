package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId

/**
 * Command (actor: utente, S2 → "Elimina…" → confirm): deletes [registrazioneId] for good — its audio copy, Trascritto,
 * Elaborazioni, Documento and the Parlanti data keyed by its Voci (ADR 0020, INV-28). Refused while one of its
 * Elaborazioni is open; it never cancels a queued one implicitly [user default].
 */
public data class EliminaRegistrazione(public val registrazioneId: RegistrazioneId)
