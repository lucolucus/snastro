plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ProvisioningModelli/ErroreModelli expose kernel Esito/ErroreDominio in their public API: `api`.
    api(project(":kernel"))

    testImplementation(testFixtures(project(":kernel")))
}
