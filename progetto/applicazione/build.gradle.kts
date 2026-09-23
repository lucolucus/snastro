plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Published events (applicazione.eventi) expose kernel Published Language types: `api`.
    api(project(":kernel"))

    // AC-14/AC-15 shape checks of the published events read their declarations (CR-5 via Konsist).
    testImplementation(libs.konsist)
}

dependencies {
    // Repository ports speak the context's own aggregates (Progetto, Registrazione): `api`.
    api(project(":progetto:dominio"))

    // Fakes implement kernel `Ripristinabile`; contracts use the kernel test helpers (`atteso`).
    testFixturesApi(testFixtures(project(":kernel")))
}
