plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Ports (applicazione.porte) expose kernel Published Language types (RegistrazioneId, VoceRef): `api`.
    api(project(":kernel"))

    // Port Finte mint ids like the supplier, with the kernel's GeneratoreIdFinto.
    testImplementation(testFixtures(project(":kernel")))
}
