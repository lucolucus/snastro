import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("snastro.kotlin-jvm")
}

// Opt-in real-native contracts (ADR 0005/0016): DecodificatoreAudioFfmpegTest (real FFmpeg, AC-151/AC-152)
// and EstrattoreImprontaSherpaModelliTest (real sherpa-onnx + TitaNet-small, AC-258/AC-493) are
// `@Tag("modelli")`, so the default `test` task (which excludes "modelli", dev-architecture-app.md#test)
// never runs them. A dedicated Test task configures its OWN useJUnitPlatform and does not inherit that
// exclusion. Never wired into `check`. `scaricaNativiSherpa` + `sherpa_onnx.native.path` mirror
// `:ml-sherpa`'s own `modelliTest` (ADR 0016 §4). decodifica-parlanti, estrattore-impronta-sherpa
val scaricaNativiSherpa = rootProject.tasks.named("scaricaNativiSherpa")

tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: real FFmpeg / sherpa-onnx contracts (@Tag(\"modelli\"), AC-151/AC-152/AC-258/AC-493)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    forkEvery = 1 // one JVM per test class: sherpa's natives load once per JVM (MotoreSherpa).
    maxHeapSize = "2g" // AC-493 holds a 75-min recording as 16 kHz floats (~300 MB)
    dependsOn(scaricaNativiSherpa)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dsherpa_onnx.native.path=${scaricaNativiSherpa.get().outputs.files.singleFile.absolutePath}")
    }
}

dependencies {
    // Ports this module implements (LettoreRegistrazione / LettoreVoci / DecodificatoreAudio) and
    // consumes (ParlanteRepository / AttribuzioneRepository, ConfrontoImpronte, ApplicaRevisionePolitica,
    // RiallineaImpronteServizio) + the Parlante / Attribuzione aggregates and kernel types reached
    // transitively (applicazione exposes :kernel and its own dominio as `api`).
    implementation(project(":parlanti:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) — the supplier side of
    // LettoreRegistrazioneDaProgetto (boundary registrazione-per-parlanti, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori).
    // registrazione-da-progetto-pa
    implementation(project(":progetto:applicazione"))

    // VociDelTrascritto (supplier side of LettoreVociDaTrascrizione, boundary voci-per-parlanti) and
    // VociUnite/VoceDivisa/SegmentoRiassegnato (eventi-revisione, consumed by AbbonatoRevisioneParlanti /
    // AbbonatoRiallineamentoImpronte) — Trascrizione's public applicazione API only (CR-1).
    // lettore-voci-da-trascrizione, abbonato-revisione-parlanti, abbonato-riallineamento-impronte
    implementation(project(":trascrizione:applicazione"))

    // DecodificatoreAudioFfmpeg delegates to :audio's real FFmpeg decode, never touching
    // org.bytedeco directly (ADR 0005, CR-3 confinement). decodifica-parlanti
    implementation(project(":audio"))

    // EstrattoreImprontaSherpa delegates to :ml-sherpa's EmbeddingSherpa (native load, Mutex, model cache,
    // ADR 0019 §1.4) — com.k2fsa itself never appears here (CR-3). estrattore-impronta-sherpa
    implementation(project(":ml-sherpa"))

    // ParlanteRepositorySql / AttribuzioneRepositorySql (..adattatori.persistenza) run on the
    // generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0009/0012, CR-3 confinement).
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping the parlante_nome_attivo_unico violation
    // (INV-16, ADR 0007) to ErroreParlanti.NomeGiaInUso (CR-3 confinement allows org.sqlite here).
    implementation(libs.sqlite.jdbc)

    // AbbonatoRiallineamentoImpronte's background coalescing/retry coroutine (ADR 0012).
    implementation(libs.kotlinx.coroutines.core)

    // The ports' contracts + fakes (ParlanteRepositoryContratto, AttribuzioneRepositoryContratto,
    // LettoreRegistrazioneContratto, LettoreVociContratto, ConfrontoImpronteContratto,
    // DecodificatoreAudioContratto, Ambiente* / Seme*, the port Finte) — D1/D2: this module's adapter
    // tests extend the port contracts (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Progetto's and Trascrizione's own commands + their port fakes — the cross-context D2 tests seed
    // each supplier only through ITS OWN commands, never its aggregates or SQL repositories (CR-1).
    // registrazione-da-progetto-pa, lettore-voci-da-trascrizione
    testImplementation(testFixtures(project(":progetto:applicazione")))
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // databaseInMemoria() / apriDatabaseProgetto (testFixtures + main) — a fresh in-memory
    // SnastroDatabase per contract test, a file-backed one for the concurrency/checkpoint tests.
    testImplementation(testFixtures(project(":persistenza")))

    // motoreSherpaSenzaNativi / ModelloEmbeddingFinto: EstrattoreImprontaSherpa's gate tests on the REAL
    // Mutex and sessions with no native library and no model loaded (AC-406/407/492).
    testImplementation(testFixtures(project(":ml-sherpa")))

    // AC-622 (ADR 0020): the checkpoint test counts `wal_checkpoint` statements through a delegating SqlDriver over
    // its own in-memory JdbcSqliteDriver. repository-sql-parlanti
    testImplementation(libs.sqldelight.driver)

    // Ripristinabile / UnitaDiLavoroFinta / ErroreDiProva / Esito test helpers.
    testImplementation(testFixtures(project(":kernel")))

    // AbbonatoRiallineamentoImpronteTest: virtual time (StandardTestDispatcher/runTest, no real
    // sleeps — dev-architecture-app.md#test) to drive the coalescing/backoff coroutine deterministically.
    testImplementation(libs.kotlinx.coroutines.test)
}
