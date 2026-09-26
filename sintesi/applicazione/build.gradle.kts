plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Ports (applicazione.porte) expose kernel Published Language types (Esito, ErroreDominio): `api`.
    api(project(":kernel"))

    // Port Finte guard on UnitaDiLavoroFinta; Contratti use the kernel Esito test helpers — both reach
    // the modules that subclass the Contratti.
    testFixturesApi(testFixtures(project(":kernel")))
}
