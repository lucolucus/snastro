plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // LettoreNomi (port this module implements) + kernel Published Language types reached
    // transitively (applicazione exposes :kernel as `api`).
    implementation(project(":sintesi:applicazione"))

    // NomiDelleVoci (Parlanti's public read API) — the supplier side of LettoreNomiDaParlanti
    // (boundary nomi-per-sintesi, ADR 0002/0021: consumer:adattatori -> supplier:applicazione
    // only, never supplier:adattatori). lettore-nomi-da-parlanti-sintesi
    implementation(project(":parlanti:applicazione"))

    // LettoreNomiContratto + AmbienteLettoreNomi + ParlanteSeminato/RegistrazioneSeminata
    // (testFixtures) — D2: this module's adapter test extends the port contract
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":sintesi:applicazione")))

    // Parlanti's own commands (ConfermaAttribuzioneServizio, RinominaParlanteServizio,
    // EliminaParlanteServizio) + its port fakes (ParlanteRepositoryFinta, AttribuzioneRepositoryFinta,
    // LettoreRegistrazioneFinta, LettoreVociFinta, DecodificatoreAudioFinta, EstrattoreImprontaFinta) —
    // this test (AC-S53) seeds the supplier only through ITS OWN commands over its own in-memory port
    // fakes, never Parlanti's SQL repositories (ADR 0002/0021 §2: :sintesi:adattatori has no edge to
    // :parlanti:adattatori).
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps
    // an expected `Esito.Ok` in the test.
    testImplementation(testFixtures(project(":kernel")))

    // RiassuntoRepositorySql / LunghezzaMassimaRiassuntoRepositorySql (..adattatori.persistenza) run on the
    // generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0009/0012/0022, CR-3 confinement).
    // repository-sql-sintesi
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping riassunto_non_pronto_unico / riassunto_pronto_unico
    // violations (ADR 0007/0022) to ErroreSintesi.RiassuntoGiaAperto (CR-3 confinement allows org.sqlite here).
    // Also gives org.sqlite.SQLiteConfig to the D2 tests below. repository-sql-sintesi
    implementation(libs.sqlite.jdbc)

    // app.cash.sqldelight's JdbcSqliteDriver: the D2 tests' own in-memory seeding of `progetto`/`registrazione`
    // parent rows via RAW SQL on the driver (never `progettoQueries`/`registrazioneQueries` — ADR 0021 clause 2
    // forbids Sintesi from using another context's generated queries, even in tests). repository-sql-sintesi
    implementation(libs.sqldelight.driver)

    // RiassuntoRepositoryContratto / LunghezzaMassimaRiassuntoRepositoryContratto + PredisposizioneSintesi +
    // RiassuntiDiProva builders (unRiassunto/conAvvio/conCompletamento/unaStruttura) — D2: this module's adapter
    // tests extend the port contracts (dev-architecture-app.md#porta-contratto). Reaches the existing
    // testImplementation(testFixtures(project(":sintesi:applicazione"))) line above. repository-sql-sintesi

    // databaseInMemoria() / apriDatabaseProgetto (testFixtures + main) — a fresh in-memory SnastroDatabase per
    // contract test, a real FILE one for the AC-S113 CAS race (BEGIN IMMEDIATE only serialises a file DB).
    // repository-sql-sintesi
    testImplementation(testFixtures(project(":persistenza")))
}
