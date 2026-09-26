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
}
