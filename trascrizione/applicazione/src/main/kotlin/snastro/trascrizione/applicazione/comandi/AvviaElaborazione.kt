package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId

/**
 * Enqueues a new `Elaborazione` `in_attesa` for [registrazioneId]. The only actor is the utente: 'Trascrivi' on a
 * Registrazione without an Elaborazione, 'Riprova' after `fallita` (ADR 0014), 'Ritrascrivi' after `completata`
 * (ADR 0018 — the same command; it never touches the Trascritto). [numeroPersone] is the optional Numero di
 * persone (1..10; `null` = automatic clustering). See [AvviaElaborazioneServizio].
 */
public data class AvviaElaborazione(val registrazioneId: RegistrazioneId, val numeroPersone: Int? = null)
