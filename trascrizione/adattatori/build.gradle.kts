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
    // IntervalloMs) reached transitively (applicazione exposes :kernel and its own dominio as `api`):
    // DecodificatoreAudio (decodifica-trascrizione); Allineatore + RiconoscitoreParlato / Vad / Turno /
    // SegmentoGrezzo (allineatore); ElaborazioneRepository / TrascrittoRepository + the Elaborazione /
    // Trascritto aggregates (repository-sql-trascrizione).
    implementation(project(":trascrizione:applicazione"))

    // DecodificatoreAudioFfmpeg delegates to :audio's real FFmpeg decode/probe, never touching
    // org.bytedeco/javax.sound directly (ADR 0005, CR-3 confinement).
    implementation(project(":audio"))

    // The generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0012).
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping a unique-constraint violation (INV-4, ADR
    // 0007) to ElaborazioneGiaAperta/ElaborazioneGiaCompletata (CR-3 confinement allows org.sqlite here).
    implementation(libs.sqlite.jdbc)

    // The ports' contracts + fakes (DecodificatoreAudioContratto, AllineatoreContratto,
    // ElaborazioneRepositoryContratto, TrascrittoRepositoryContratto, RiconoscitoreParlatoFinta /
    // VadFinta, synthetic-signal helpers, dominio fixtures) — D2: this module's adapter tests extend
    // the port contracts (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // databaseInMemoria() (testFixtures) — a fresh in-memory SnastroDatabase per contract test.
    testImplementation(testFixtures(project(":persistenza")))
}
