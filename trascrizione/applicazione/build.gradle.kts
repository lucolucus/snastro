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

    // Repository ports (applicazione.porte) expose the Trascrizione domain types (Elaborazione, Trascritto).
    api(project(":trascrizione:dominio"))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); Contratti use the kernel
    // Esito test helpers — both reach the modules that subclass the Contratti.
    testFixturesApi(testFixtures(project(":kernel")))

    // Contratti, Finte and their tests build aggregates through the dominio fixtures (never `ricostituisci`,
    // CR-15); `api` so the subclasses of the Contratti (and their tests) reach the same builders.
    testFixturesApi(testFixtures(project(":trascrizione:dominio")))
}
