plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ElaborazioneRepositorySql / TrascrittoRepositorySql (..adattatori.persistenza): ElaborazioneRepository
    // / TrascrittoRepository (applicazione.porte) + the Elaborazione/Trascritto aggregates reach
    // transitively (applicazione exposes its own dominio as `api`).
    implementation(project(":trascrizione:applicazione"))

    // The generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0012).
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping a unique-constraint violation (INV-4, ADR
    // 0007) to ElaborazioneGiaAperta/ElaborazioneGiaCompletata (CR-3 confinement allows org.sqlite here).
    implementation(libs.sqlite.jdbc)

    // ElaborazioneRepositoryContratto + TrascrittoRepositoryContratto (testFixtures) — D2: this
    // adapter's own test classes extend the contracts (dev-architecture-app.md#porta-contratto). The
    // dominio fixtures (unaElaborazione, unTrascritto, ...) reach transitively (applicazione's
    // testFixtures exposes dominio's testFixtures as `api`).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // databaseInMemoria() (testFixtures) — a fresh in-memory SnastroDatabase per contract test.
    testImplementation(testFixtures(project(":persistenza")))
}
