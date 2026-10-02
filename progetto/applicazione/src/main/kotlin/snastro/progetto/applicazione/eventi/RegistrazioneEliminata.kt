package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Published Language of the domain event `RegistrazioneEliminata` (boundary `eventi-progetto`, ADR 0020). Published
 * by `EliminaRegistrazione` INSIDE its transaction, BEFORE the registrazione row is removed; titolo, data and
 * riferimento are the values at deletion — the only way after-commit consumers can locate the files.
 * [incontroId] is the Incontro the Parte belonged to; [incontroCessato] is true iff no other Parte of it exists, so the
 * Incontro ceases in this same transaction (ADR 0038 §1).
 * Delivery EXCEPTION (ADR 0038 §2): TWO SYNCHRONOUS subscribers, in the declared module order (ADR 0030 §2), whose
 * Errore dooms the command:
 * - Sintesi: with [incontroCessato], every Riassunto of the Incontro goes;
 * - Trascrizione: the veto on an open Elaborazione, then its purge, which publishes `TrascrittoEliminato` to the
 *   Parlanti purge + INV-25 (nested).
 * Parlanti no longer subscribes synchronously to this event. Every other subscriber runs after commit (the Sbobinatura
 * removal, the file cleanup, the Parlanti view refresh).
 */
public data class RegistrazioneEliminata(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val riferimentoAudio: RiferimentoAudio,
    val incontroId: IncontroId,
    val incontroCessato: Boolean,
) : EventoPubblicato
