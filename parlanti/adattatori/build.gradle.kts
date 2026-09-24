plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ParlanteRepository / AttribuzioneRepository (repo-parlanti) + the Parlante / Attribuzione
    // aggregates reached transitively (applicazione exposes :kernel and its own dominio as `api`).
    implementation(project(":parlanti:applicazione"))

    // ParlanteRepositorySql / AttribuzioneRepositorySql (..adattatori.persistenza) run on the
    // generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0009/0012, CR-3 confinement).
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping the parlante_nome_attivo_unico violation
    // (INV-16, ADR 0007) to ErroreParlanti.NomeGiaInUso (CR-3 confinement allows org.sqlite here).
    implementation(libs.sqlite.jdbc)

    // The ports' contracts + fakes (ParlanteRepositoryContratto, AttribuzioneRepositoryContratto,
    // PredisposizioneParlanti) — D2: this module's adapter tests extend the port contracts
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // databaseInMemoria() / apriDatabaseProgetto (testFixtures + main) — a fresh in-memory
    // SnastroDatabase per contract test, a file-backed one for the concurrency/checkpoint tests.
    testImplementation(testFixtures(project(":persistenza")))
}
