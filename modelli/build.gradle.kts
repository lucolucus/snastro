plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ProvisioningModelli/ErroreModelli expose kernel Esito/ErroreDominio in their public API: `api`.
    api(project(":kernel"))
    // Archive extraction only (BZip2CompressorInputStream + TarArchiveInputStream), never exposed
    // in this module's public API: `implementation` (ADR 0008 Amendment (c), CR-3).
    implementation(libs.commons.compress)

    testImplementation(testFixtures(project(":kernel")))
}
