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
}
