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
    // LettoreRegistrazione / LettoreVoci / DecodificatoreAudio (ports this module implements) + kernel
    // Published Language types reached transitively (applicazione exposes :kernel as `api`).
    implementation(project(":parlanti:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) — the supplier side of
    // LettoreRegistrazioneDaProgetto (boundary registrazione-per-parlanti, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori).
    // registrazione-da-progetto-pa
    implementation(project(":progetto:applicazione"))

    // VociDelTrascritto (Trascrizione's public read API) — the supplier side of
    // LettoreVociDaTrascrizione (boundary voci-per-parlanti, ADR 0002).
    // lettore-voci-da-trascrizione
    implementation(project(":trascrizione:applicazione"))

    // LettoreRegistrazioneContratto / LettoreVociContratto + Ambiente* + Seme* (testFixtures) — D2:
    // this module's adapter tests extend the port contracts (dev-architecture-app.md#porta-contratto).
    // registrazione-da-progetto-pa, lettore-voci-da-trascrizione
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio,
    // ModificaDataRegistrazioneServizio) + its port fakes (ProgettoRepositoryFinta,
    // RegistrazioneRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) — D2 seeds the supplier only
    // through ITS OWN commands, never by constructing its aggregates.
    // registrazione-da-progetto-pa, lettore-voci-da-trascrizione (Registrazione scaffolding)
    testImplementation(testFixtures(project(":progetto:applicazione")))

    // Trascrizione's own commands (AvviaElaborazioneServizio, EseguiProssimaElaborazioneServizio,
    // UnisciVociServizio, DividiVoceServizio, RiassegnaSegmentoServizio) + its port fakes
    // (ElaborazioneRepositoryFinta, TrascrittoRepositoryFinta, DecodificatoreAudioFinta,
    // DiarizzatoreFinta, AllineatoreFinta, SegnalatoreFaseFinta, LettoreRegistrazioneFinta) —
    // LettoreVociDaTrascrizioneTest (D2) seeds the supplier only through ITS OWN commands, never its
    // SQL repositories (ADR 0002, CR-1).
    // lettore-voci-da-trascrizione
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // DecodificatoreAudioFfmpeg delegates to :audio's real FFmpeg decode, never touching
    // org.bytedeco directly (ADR 0005, CR-3 confinement). decodifica-parlanti
    implementation(project(":audio"))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps
    // an expected `Esito.Ok` in the test.
    testImplementation(testFixtures(project(":kernel")))
}
