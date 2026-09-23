import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.kotlin-jvm")
}

// Opt-in real-FFmpeg contract (AC-149/AC-150, ADR 0005): the whole `DecodificatoreAudioFfmpegTest`
// class is `@Tag("modelli")` (every contract case decodes through real FFmpeg first), so the default
// `test` task (which excludes "modelli", dev-architecture-app.md#test) runs none of it. A dedicated
// `Test` task configures its OWN `useJUnitPlatform` and does not inherit that exclusion (the comment
// on `configureTesting()` in build-logic). Never wired into `check`.
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: DecodificatoreAudioFfmpegTest against real FFmpeg (@Tag(\"modelli\"), AC-149/AC-150)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
}

dependencies {
    // Ports + kernel Published Language types (RegistrazioneId, RiferimentoAudio, CampioniAudio,
    // IntervalloMs) reached transitively (applicazione exposes :kernel as `api`):
    // DecodificatoreAudio (decodifica-trascrizione); Allineatore + RiconoscitoreParlato / Vad / Turno /
    // SegmentoGrezzo (allineatore).
    implementation(project(":trascrizione:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) + its RegistrazioneVista — the supplier
    // side of LettoreRegistrazioneDaProgetto (boundary registrazione-per-trascrizione, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori).
    implementation(project(":progetto:applicazione"))

    // DecodificatoreAudioFfmpeg delegates to :audio's real FFmpeg decode/probe, never touching
    // org.bytedeco/javax.sound directly (ADR 0005, CR-3 confinement).
    implementation(project(":audio"))

    // The ports' contracts + fakes (DecodificatoreAudioContratto, AllineatoreContratto,
    // RiconoscitoreParlatoFinta / VadFinta, synthetic-signal helpers) — D2: this module's adapter tests
    // extend the port contracts (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio,
    // ModificaDataRegistrazioneServizio) + its port fakes (RegistrazioneRepositoryFinta,
    // ProgettoRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) — LettoreRegistrazioneDaProgettoTest
    // (D2) seeds the supplier only through ITS OWN commands, never by constructing its aggregates.
    testImplementation(testFixtures(project(":progetto:applicazione")))
}
