package snastro.persistenza

/**
 * Seeds the bare `trascritto`/`voce` rows a foreign key needs, for a repository test OUTSIDE
 * `:trascrizione` (parlanti's SQL persistence round-trip tests) that only needs the parent rows
 * to exist and never re-implements `Trascritto`'s rule (dev-architecture-app.md#repository).
 *
 * The `agg-trascritto` §14 gate confines `trascrittoQueries`/`voceQueries` to `:persistenza` and
 * trascrizione's own persistence adapter — `:persistenza` already owns the generated queries and
 * the schema (ADR 0006), so this pair of functions, not a raw cross-context call, is the allowed
 * seam a `parlanti:adattatori` test uses to seed them (`testImplementation(testFixtures(":persistenza"))`
 * is already a dependency there).
 */
public fun SnastroDatabase.seminaTrascrittoDiProva(registrazioneId: String, prossimaVoce: Long = 1L) {
    trascrittoQueries.inserisci(registrazioneId = registrazioneId, prossimaVoce = prossimaVoce, prossimoSegmento = 1L)
}

/** Seeds a bare `voce` row (no `segmento`), for the same purpose as [seminaTrascrittoDiProva]. */
public fun SnastroDatabase.seminaVoceDiProva(registrazioneId: String, numero: Long) {
    voceQueries.inserisci(registrazioneId = registrazioneId, numero = numero)
}
