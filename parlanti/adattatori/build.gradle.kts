plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ConfrontoImpronte (tec-confronto-impronte, this block).
    implementation(project(":parlanti:applicazione"))

    // ConfrontoImpronteContratto (AC-127) — D2 (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":parlanti:applicazione")))
}
