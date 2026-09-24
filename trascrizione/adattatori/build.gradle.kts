import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("snastro.kotlin-jvm")
}

// Opt-in real-native contract (ADR 0004/0005/0013/0015/0016): the whole `DecodificatoreAudioFfmpegTest`
// (real FFmpeg, AC-149/AC-150), `VadSileroTest` (real Silero VAD, AC-255), `DiarizzatoreSherpaTest` (real
// sherpa-onnx diarization, AC-249/AC-373) and `RiconoscitoreParlatoSherpaContrattoTest` (real Parakeet,
// AC-252/AC-388) classes are `@Tag("modelli")`, so the default `test` task (which excludes "modelli",
// dev-architecture-app.md#test) runs none of them. A dedicated `Test` task configures its OWN
// `useJUnitPlatform` and does not inherit that exclusion (the comment on `configureTesting()` in
// build-logic). Never wired into `check`. `scaricaNativiSherpa` + `sherpa_onnx.native.path` mirror
// `:ml-sherpa`'s own `modelliTest` (AC-243/244, ADR 0016 §4).
val scaricaNativiSherpa = rootProject.tasks.named("scaricaNativiSherpa")

tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: real FFmpeg/sherpa-onnx contracts (@Tag(\"modelli\"), " +
        "AC-149/AC-150/AC-252/AC-255/AC-249/AC-373/AC-388/AC-488/AC-489/AC-491)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    forkEvery = 1 // one JVM per test class: sherpa's natives load once per JVM (MotoreSherpa).
    // AC-488/489 hold a 75-min recording (and one shifted copy) as 16 kHz floats: ~300 MB each.
    maxHeapSize = "4g"
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

    // VadSilero, DiarizzatoreSherpa and RiconoscitoreParlatoSherpa (real Vad / Diarizzatore /
    // RiconoscitoreParlato adapters, ADR 0004/0016) delegate to :ml-sherpa's MotoreSherpa /
    // SessioneSherpa / RiconoscitoreSherpa (native load, Mutex, resource registration) — com.k2fsa
    // itself never appears here (CR-3 confinement stays inside :ml-sherpa).
    implementation(project(":ml-sherpa"))

    // The generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0012).
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping a unique-constraint violation (INV-4, ADR
    // 0007) to ElaborazioneGiaAperta (CR-3 confinement allows org.sqlite here).
    implementation(libs.sqlite.jdbc)

    // The ports' contracts + fakes (DecodificatoreAudioContratto, AllineatoreContratto,
    // ElaborazioneRepositoryContratto, TrascrittoRepositoryContratto, RiconoscitoreParlatoFinta /
    // VadFinta, synthetic-signal helpers, dominio fixtures) — D2: this module's adapter tests extend
    // the port contracts (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // motoreSherpaSenzaNativi / ModelloEmbeddingFinto: DiarizzatoreSherpa's gate tests on the REAL Mutex and
    // sessions with no native library and no model loaded (AC-486/AC-491, ADR 0019 §1.5).
    testImplementation(testFixtures(project(":ml-sherpa")))

    // databaseInMemoria() (testFixtures) — a fresh in-memory SnastroDatabase per contract test.
    testImplementation(testFixtures(project(":persistenza")))

    // AC-445 (ADR 0018): the deferred-FK backstop test seeds/purges Parlanti rows with RAW SQL on its own
    // in-memory JdbcSqliteDriver — never through the generated Parlanti queries (ADR 0018 enforced_by).
    testImplementation(libs.sqldelight.driver)

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio,
    // ModificaDataRegistrazioneServizio) + its port fakes (RegistrazioneRepositoryFinta,
    // ProgettoRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) — LettoreRegistrazioneDaProgettoTest
    // (D2) seeds the supplier only through ITS OWN commands, never by constructing its aggregates.
    testImplementation(testFixtures(project(":progetto:applicazione")))
}
