plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // The ports this module implements (LettoreTrascritto, LettoreNomi) + kernel Published Language types reached
    // transitively (applicazione exposes :kernel as `api`).
    implementation(project(":sintesi:applicazione"))

    // Supplier read APIs, consumer:adattatori -> supplier:applicazione only (ADR 0002/0021 §2-3, AC-S52):
    // VociDelTrascritto + StatiElaborazione (boundary trascritto-per-sintesi) and NomiDelleVoci (nomi-per-sintesi).
    implementation(project(":trascrizione:applicazione"))
    implementation(project(":parlanti:applicazione"))

    // Progetto's published event RegistrazioneEliminata (boundary eventi-progetto, ADR 0002/0021 §2-3:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori) — abbonato-progetto-sintesi
    // translates it into ApplicaEliminazioneRegistrazioneSintesiPolitica.applica (ADR 0020 §2 step 4 / ADR 0024 §1).
    implementation(project(":progetto:applicazione"))

    // Port contracts + Ambienti (testFixtures) — D2: the adapter tests extend the port contracts
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":sintesi:applicazione")))

    // The suppliers' own commands + their in-memory port fakes: D2 seeds each supplier only through ITS OWN
    // commands, never its SQL repositories (:sintesi:adattatori has no edge to supplier:adattatori).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps an
    // expected `Esito.Ok` in the test; GeneratoreIdFinto mints deterministic ids.
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
