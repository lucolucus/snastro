import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.kotlin-jvm")
}

// sherpa-onnx natives for VadSileroTest (AC-255, ADR 0004/0016): fetched by the root task, never
// by `check` — the same edge :ml-sherpa's own modelliTest task declares.
val scaricaNativiSherpa = rootProject.tasks.named("scaricaNativiSherpa")

// Opt-in real-native contract (AC-149/AC-150/AC-255, ADR 0004/0005): the whole `DecodificatoreAudioFfmpegTest`
// (real FFmpeg) and `VadSileroTest` (real Silero VAD) classes are `@Tag("modelli")`, so the default
// `test` task (which excludes "modelli", dev-architecture-app.md#test) runs neither. A dedicated
// `Test` task configures its OWN `useJUnitPlatform` and does not inherit that exclusion (the comment
// on `configureTesting()` in build-logic). Never wired into `check`.
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: DecodificatoreAudioFfmpegTest against real FFmpeg (AC-149/AC-150) and " +
        "VadSileroTest against the real Silero VAD (AC-255), both @Tag(\"modelli\")."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    dependsOn(scaricaNativiSherpa)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dsherpa_onnx.native.path=${scaricaNativiSherpa.get().outputs.files.singleFile.absolutePath}")
    }
}

dependencies {
    // Ports + kernel Published Language types (RegistrazioneId, RiferimentoAudio, CampioniAudio,
    // IntervalloMs) reached transitively (applicazione exposes :kernel and its own dominio as `api`):
    // DecodificatoreAudio (decodifica-trascrizione); Allineatore + RiconoscitoreParlato / Vad / Turno /
    // SegmentoGrezzo (allineatore); ElaborazioneRepository / TrascrittoRepository + the Elaborazione /
    // Trascritto aggregates (repository-sql-trascrizione).
    implementation(project(":trascrizione:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) + its RegistrazioneVista — the supplier
    // side of LettoreRegistrazioneDaProgetto (boundary registrazione-per-trascrizione, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori).
    implementation(project(":progetto:applicazione"))

    // DecodificatoreAudioFfmpeg delegates to :audio's real FFmpeg decode/probe, never touching
    // org.bytedeco/javax.sound directly (ADR 0005, CR-3 confinement).
    implementation(project(":audio"))

    // VadSilero (real Vad adapter, boundary tec-vad, ADR 0004/0016) delegates to MotoreSherpa /
    // ConfigSessione / RilevatoreSilero — the only sherpa-onnx (com.k2fsa) wrapper this module may
    // call into; com.k2fsa itself never appears here (CR-3 confinement stays inside :ml-sherpa).
    implementation(project(":ml-sherpa"))

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

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio,
    // ModificaDataRegistrazioneServizio) + its port fakes (RegistrazioneRepositoryFinta,
    // ProgettoRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) — LettoreRegistrazioneDaProgettoTest
    // (D2) seeds the supplier only through ITS OWN commands, never by constructing its aggregates.
    testImplementation(testFixtures(project(":progetto:applicazione")))
}
