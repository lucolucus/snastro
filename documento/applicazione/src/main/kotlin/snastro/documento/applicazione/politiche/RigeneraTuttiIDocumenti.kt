package snastro.documento.applicazione.politiche

/**
 * Command `RigeneraTuttiIDocumenti` (actor: sistema, startup, ADR 0012 Amendment R4, AC-158):
 * `Documento` neither persists nor reads back its own `.md` (ADR 0010), so there is no
 * `documento_generato_versione` to compare against — every Registrazione with a Trascritto is
 * regenerated unconditionally instead. Idempotent and cheap by construction ([RigeneraDocumento]
 * writes byte-identical content for unchanged inputs, [INV-23]).
 */
public data object RigeneraTuttiIDocumenti
