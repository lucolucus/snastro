plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Published events (applicazione.eventi) expose kernel Published Language types: `api`.
    api(project(":kernel"))

    // AC-14/AC-15 shape checks of the published events read their declarations (CR-5 via Konsist).
    testImplementation(libs.konsist)

    // LettoreRegistrazioneFintaTest mints ids like the supplier, with the kernel's GeneratoreIdFinto.
    testImplementation(testFixtures(project(":kernel")))
}
