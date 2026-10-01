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
 * Delivery EXCEPTION: TWO SYNCHRONOUS subscribers (the Trascrizione veto + purge, the Parlanti purge + INV-25), whose
 * Errore dooms the command; every other subscriber runs after commit (the Sbobinatura removal, the file cleanup).
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
