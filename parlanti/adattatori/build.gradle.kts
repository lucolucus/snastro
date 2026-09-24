import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.kotlin-jvm")
}

// Opt-in real-native contract (ADR 0005): DecodificatoreAudioFfmpegTest (real FFmpeg, AC-151/AC-152)
// is `@Tag("modelli")`, so the default `test` task (which excludes "modelli", dev-architecture-app.md#test)
// never runs it. A dedicated Test task configures its OWN useJUnitPlatform and does not inherit that
// exclusion. Never wired into `check`. decodifica-parlanti
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: real FFmpeg contract (@Tag(\"modelli\"), AC-151/AC-152)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
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

    // Ripristinabile / UnitaDiLavoroFinta / ErroreDiProva / Esito test helpers.
    testImplementation(testFixtures(project(":kernel")))

    // AbbonatoRiallineamentoImpronteTest: virtual time (StandardTestDispatcher/runTest, no real
    // sleeps — dev-architecture-app.md#test) to drive the coalescing/backoff coroutine deterministically.
    testImplementation(libs.kotlinx.coroutines.test)
}
