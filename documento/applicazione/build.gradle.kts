plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // Ports (applicazione.porte) expose kernel Published Language types: `api`.
    api(project(":kernel"))

    // LettoreTrascrittoFintaTest mints ids like the supplier, with the kernel's GeneratoreIdFinto.
    testImplementation(testFixtures(project(":kernel")))
}
