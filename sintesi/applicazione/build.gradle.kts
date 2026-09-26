plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Ports (applicazione.porte) expose kernel Published Language types (Esito, ErroreDominio, RegistrazioneId, VoceRef): `api`.
    api(project(":kernel"))

    // Repository ports (applicazione.porte) expose the Sintesi domain types (Riassunto, LunghezzaMassimaRiassunto).
    api(project(":sintesi:dominio"))

    // AC-S71 shape checks of the published events read their declarations (CR-5 via Konsist).
    testImplementation(libs.konsist)

    // Port Finte guard on UnitaDiLavoroFinta and mint ids with GeneratoreIdFinto; Contratti use the kernel Esito
    // test helpers — both reach the modules that subclass the Contratti.
    testFixturesApi(testFixtures(project(":kernel")))
}
