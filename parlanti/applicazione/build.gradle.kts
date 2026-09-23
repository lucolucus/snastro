plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Published events (applicazione.eventi) expose kernel Published Language types: `api`.
    api(project(":kernel"))

    // Ports (applicazione.porte) expose the Parlanti domain types (Parlante, Attribuzione, Nome, Impronta).
    api(project(":parlanti:dominio"))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); Contratti use the
    // kernel Esito test helpers — both reach the modules that subclass the Contratti.
    testFixturesApi(testFixtures(project(":kernel")))

    // AC-14/AC-15 shape checks of the published events read their declarations (CR-5 via Konsist).
    testImplementation(libs.konsist)
}
