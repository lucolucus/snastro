plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // LettoreRegistrazione / LettoreVoci / DecodificatoreAudio (ports this module implements) + kernel
    // Published Language types reached transitively (applicazione exposes :kernel as `api`).
    implementation(project(":parlanti:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) — the supplier side of
    // LettoreRegistrazioneDaProgetto (boundary registrazione-per-parlanti, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori).
    // registrazione-da-progetto-pa
    implementation(project(":progetto:applicazione"))

    // LettoreRegistrazioneContratto + AmbienteLettoreRegistrazione + Seme* (testFixtures) — D2: this
    // module's adapter test extends the port contract (dev-architecture-app.md#porta-contratto).
    // registrazione-da-progetto-pa
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio,
    // ModificaDataRegistrazioneServizio) + its port fakes (ProgettoRepositoryFinta,
    // RegistrazioneRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) — LettoreRegistrazioneDaProgettoTest
    // (D2) seeds the supplier only through ITS OWN commands, never by constructing its aggregates.
    // registrazione-da-progetto-pa
    testImplementation(testFixtures(project(":progetto:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps
    // an expected `Esito.Ok` in the test.
    testImplementation(testFixtures(project(":kernel")))
}
