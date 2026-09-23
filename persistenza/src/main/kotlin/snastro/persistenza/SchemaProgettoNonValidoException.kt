package snastro.persistenza

/**
 * A project's database file carries schema `user_version = 1` — a baseline that was NEVER shipped
 * (ADR 0006 Amendment (a) / CR-13: `1.sqm` is the 1→2 migration step; this app only ever writes
 * `user_version` 0 or the current [SnastroDatabase.Schema.version], never 1 — the generated
 * `Schema.migrate` treats `oldVersion <= 1` identically to `oldVersion == 0`). A persisted version 1
 * cannot be a real prior release: refused exactly like a newer-than-supported schema (AC-12) — the
 * file is left untouched, nothing is created or migrated.
 */
public class SchemaProgettoNonValidoException(public val versioneTrovata: Long) :
    SchemaProgettoRifiutatoException(
        "Il file di progetto ha uno schema non valido (versione $versioneTrovata, mai rilasciata da " +
            "questa app): il file potrebbe essere corrotto.",
    )
