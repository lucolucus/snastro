package snastro.documento.applicazione.politiche

import snastro.kernel.RegistrazioneId

/**
 * Command `RigeneraDocumento` (policy `Rigenerazione`, ADR 0012): (re)writes the Documento
 * projection of [registrazioneId] under its CURRENT `nomeFile`.
 *
 * When the call follows a change that may have altered the Registrazione's `nomeFile` — a
 * `DataRegistrazioneModificata` (the date) or a `RegistrazioneRinominata` (the titolo, since
 * fix-batch-11) — the caller passes the OLD file name as [nomeFilePrecedente], computed with the
 * SAME `Documento.nomeFile` used for the new one (AC-320): the new file is written FIRST, then the
 * old one is removed, unless it names the very same file (AC-155/AC-327). `nomeFilePrecedente` is
 * deliberately a single already-computed name rather than "old data" + "old titolo": whichever of
 * the two changed, the caller (`perDataRegistrazioneModificata` / `perRegistrazioneRinominata`
 * below) combines it with the OTHER field's CURRENT value — this command itself stays agnostic of
 * which one changed.
 */
public data class RigeneraDocumento(
    val registrazioneId: RegistrazioneId,
    val nomeFilePrecedente: String? = null,
)
