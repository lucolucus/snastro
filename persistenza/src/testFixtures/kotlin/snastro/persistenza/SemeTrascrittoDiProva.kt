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
    trascrittoQueries.inserisci(registrazioneId = registrazioneId, prossimoSegmento = 1L)
    val incontroId = incontroDi(registrazioneId)
    if (vociIncontroQueries.trovaPerIncontro(incontroId).executeAsOneOrNull() == null) {
        vociIncontroQueries.inserisci(incontroId = incontroId, prossimaVoce = prossimaVoce)
    }
}

/** Seeds a bare `voce` row (no `segmento`), for the same purpose as [seminaTrascrittoDiProva]. */
public fun SnastroDatabase.seminaVoceDiProva(registrazioneId: String, numero: Long) {
    voceIncontroQueries.inserisciSeAssente(incontroId = incontroDi(registrazioneId), numero = numero)
    voceQueries.inserisci(registrazioneId = registrazioneId, numero = numero)
}

/** The Incontro the seeded Registrazione is a Parte of (test seeding only: no repository resolves it this way). */
public fun SnastroDatabase.incontroDi(registrazioneId: String): String =
    checkNotNull(registrazioneQueries.trovaPerId(registrazioneId).executeAsOne().incontro_id)

/**
 * Seeds a `registrazione` as the one Parte of its own new Incontro (ADR 0034: `incontro_id` is mandatory). The
 * Incontro id is deliberately NOT the Registrazione's id: a repository that assumed equal ids fails its test.
 */
@Suppress("LongParameterList") // one parameter per stored column of `registrazione`
public fun SnastroDatabase.seminaRegistrazioneDiProva(
    id: String,
    progettoId: String,
    titolo: String,
    riferimentoAudio: String,
    durataMs: Long,
    dataRegistrazione: String,
    aggiuntaAlle: Long,
) {
    incontroQueries.inserisci(id = "incontro-di-$id", progettoId = progettoId)
    registrazioneQueries.inserisci(
        id = id,
        progettoId = progettoId,
        incontroId = "incontro-di-$id",
        titolo = titolo,
        riferimentoAudio = riferimentoAudio,
        durataMs = durataMs,
        dataRegistrazione = dataRegistrazione,
        aggiuntaAlle = aggiuntaAlle,
    )
}
