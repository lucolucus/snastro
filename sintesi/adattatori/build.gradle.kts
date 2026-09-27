import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // The ports this module implements (LettoreTrascritto, LettoreNomi) + kernel Published Language types reached
    // transitively (applicazione exposes :kernel as `api`).
    implementation(project(":sintesi:applicazione"))

    // Supplier read APIs, consumer:adattatori -> supplier:applicazione only (ADR 0002/0021 §2-3, AC-S52):
    // VociDelTrascritto + StatiElaborazione (boundary trascritto-per-sintesi) and NomiDelleVoci (nomi-per-sintesi).
    implementation(project(":trascrizione:applicazione"))
    implementation(project(":parlanti:applicazione"))

    // Progetto's published event RegistrazioneEliminata (boundary eventi-progetto, ADR 0002/0021 §2-3:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori) — abbonato-progetto-sintesi
    // translates it into ApplicaEliminazioneRegistrazioneSintesiPolitica.applica (ADR 0020 §2 step 4 / ADR 0024 §1).
    implementation(project(":progetto:applicazione"))

    // Port contracts + Ambienti (testFixtures) — D2: the adapter tests extend the port contracts
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":sintesi:applicazione")))

    // The suppliers' own commands + their in-memory port fakes: D2 seeds each supplier only through ITS OWN
    // commands, never its SQL repositories (:sintesi:adattatori has no edge to supplier:adattatori).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps an
    // expected `Esito.Ok` in the test; GeneratoreIdFinto mints deterministic ids.
    testImplementation(testFixtures(project(":kernel")))

    // RiassuntoRepositorySql / LunghezzaMassimaRiassuntoRepositorySql (..adattatori.persistenza) run on the
    // generated SnastroDatabase queries + UnitaDiLavoro impl (ADR 0006/0009/0012/0022, CR-3 confinement).
    // repository-sql-sintesi
    implementation(project(":persistenza"))

    // org.sqlite.SQLiteException/SQLiteErrorCode: mapping riassunto_non_pronto_unico / riassunto_pronto_unico
    // violations (ADR 0007/0022) to ErroreSintesi.RiassuntoGiaAperto (CR-3 confinement allows org.sqlite here).
    // Also gives org.sqlite.SQLiteConfig to the D2 tests below. repository-sql-sintesi
    implementation(libs.sqlite.jdbc)

    // app.cash.sqldelight's JdbcSqliteDriver: the D2 tests' own in-memory seeding of `progetto`/`registrazione`
    // parent rows via RAW SQL on the driver (never `progettoQueries`/`registrazioneQueries` — ADR 0021 clause 2
    // forbids Sintesi from using another context's generated queries, even in tests). repository-sql-sintesi
    implementation(libs.sqldelight.driver)

    // RiassuntoRepositoryContratto / LunghezzaMassimaRiassuntoRepositoryContratto + PredisposizioneSintesi +
    // RiassuntiDiProva builders (unRiassunto/conAvvio/conCompletamento/unaStruttura) — D2: this module's adapter
    // tests extend the port contracts (dev-architecture-app.md#porta-contratto). Reaches the existing
    // testImplementation(testFixtures(project(":sintesi:applicazione"))) line above. repository-sql-sintesi

    // databaseInMemoria() / apriDatabaseProgetto (testFixtures + main) — a fresh in-memory SnastroDatabase per
    // contract test, a real FILE one for the AC-S113 CAS race (BEGIN IMMEDIATE only serialises a file DB).
    // repository-sql-sintesi
    testImplementation(testFixtures(project(":persistenza")))

    // attendiFinche (ADR 0028 §3, the only polling wait of the tests) — AC-C31's latch-driven concurrency case
    // (RiassuntoRepositorySqlTest).
    testImplementation(project(":supporto-test"))

    // ..ml: ModelloLinguisticoLlama over the standalone llama.cpp binding (ADR 0026, ADR 0027 §7). The gate only
    // compiles against its Kotlin API and runs the adapter over fakes of its interfaces: no native, no model.
    implementation(project(":llama-jni"))
}

// Opt-in (@Tag("modelli"), never in `check`): ModelloLinguisticoContratto against the REAL adapter over the real
// llama.cpp natives and Qwen3.5 9B (AC-S152). The natives are the library's own output (:llama-jni:assembleNatives,
// macOS arm64 only), handed through snastro.llm.native.path like :avvio does; the GGUF comes from the environment
// variable SNASTRO_MODELLO_LLM (a local verified copy, never committed; ADR 0026 §8). Aggregated by the root
// `modelliTest`.
// Only macOS arm64 is wired (D-0007): on any other host :llama-jni:assembleNatives itself fails with its message.
val nativiLlama = project(":llama-jni").layout.buildDirectory.dir("natives/macos-arm64")
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: ModelloLinguisticoContratto on the real llama.cpp adapter with Qwen3.5 9B (AC-S152)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    dependsOn(":llama-jni:assembleNatives")
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dsnastro.llm.native.path=${nativiLlama.get().asFile.absolutePath}")
    }
}
