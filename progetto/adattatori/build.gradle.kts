plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // RegistroProgetti (port) + VoceRegistro live in :progetto:applicazione's porte; ProgettoId is
    // reached transitively (applicazione exposes :kernel as `api`).
    implementation(project(":progetto:applicazione"))

    // RegistroProgettiContratto + RegistroProgettiFinta (testFixtures) — D2: this adapter's own
    // test class extends the contract (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":progetto:applicazione")))
}
