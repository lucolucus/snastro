plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ScrittoreDocumento (port) + richiediNomeFileDocumento live in :documento:applicazione's porte.
    implementation(project(":documento:applicazione"))

    // ScrittoreDocumentoContratto (testFixtures) — D2: this adapter's test class extends the
    // contract (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":documento:applicazione")))
}
