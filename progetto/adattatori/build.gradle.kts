plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // RegistroProgetti (port) + VoceRegistro live in :progetto:applicazione's porte; ProgettoId is
    // reached transitively (applicazione exposes :kernel as `api`).
    implementation(project(":progetto:applicazione"))

    // SondaAudioFfmpeg delegates to :audio's real FFmpeg probe (ADR 0005); ArchivioAudioFile is
    // plain java.nio and needs no :audio type, but both adapters live in this module's `..audio`
    // package (dev-architecture-app.md#pacchetti).
    implementation(project(":audio"))

    // RegistroProgettiContratto + RegistroProgettiFinta + SondaAudioContratto + ArchivioAudioContratto
    // (testFixtures) — D2: this adapter's own test classes extend the contracts
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":progetto:applicazione")))
}
