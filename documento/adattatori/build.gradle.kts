plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // LettoreTrascritto + ScrittoreDocumento (ports this module implements; richiediNomeFileDocumento
    // too) + kernel Published Language types reached
    // transitively (applicazione exposes :kernel as `api`).
    implementation(project(":documento:applicazione"))

    // VociDelTrascritto (Trascrizione's public read API) — half of the supplier side of
    // LettoreTrascrittoDaTrascrizione (boundary trascritto-per-documento, ADR 0002:
    // consumer:adattatori -> supplier:applicazione only, never supplier:adattatori) — AND
    // ElaborazioneCompletata/VociUnite/VoceDivisa/SegmentoRiassegnato (eventi-elaborazione,
    // eventi-revisione), consumed by AbbonatoDocumentoEventi.
    implementation(project(":trascrizione:applicazione"))

    // CatalogoRegistrazioni (Progetto's public read API) — the other half (titolo + dataRegistrazione
    // of the Registrazione), same boundary — AND DataRegistrazioneModificata/RegistrazioneRinominata
    // (eventi-progetto), consumed by AbbonatoDocumentoEventi.
    implementation(project(":progetto:applicazione"))

    // AttribuzioneConfermata/ParlanteRinominato/ParlantePromosso/ParlanteEliminato (eventi-parlanti),
    // consumed by AbbonatoDocumentoEventi (AC-186; R2 events mapped now per the manifest).
    implementation(project(":parlanti:applicazione"))

    // AbbonatoDocumentoEventi's background coalescing/retry coroutine (ADR 0012).
    implementation(libs.kotlinx.coroutines.core)

    // ScrittoreDocumentoContratto, LettoreTrascrittoContratto + AmbienteLettoreTrascritto + Seme*/​*Coniato (testFixtures) — D2:
    // this module's adapter test extends the port contract (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":documento:applicazione")))

    // Trascrizione's own commands (AvviaElaborazioneServizio, EseguiProssimaElaborazioneServizio,
    // RiassegnaSegmentoServizio) + its port fakes (ElaborazioneRepositoryFinta, TrascrittoRepositoryFinta,
    // DecodificatoreAudioFinta, DiarizzatoreFinta, AllineatoreFinta, SegnalatoreFaseFinta,
    // LettoreRegistrazioneFinta) — D2 seeds the supplier only through ITS OWN commands over its own
    // in-memory fakes, never Trascrizione's SQL repositories (ADR 0002, CR-1: consumer:adattatori
    // reaches only supplier:applicazione, never supplier:adattatori).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // Progetto's own commands (CreaProgettoServizio, AggiungiRegistrazioneServizio) + its port fakes
    // (ProgettoRepositoryFinta, RegistrazioneRepositoryFinta, SondaAudioFinta, ArchivioAudioFinta) —
    // same reason as above, for the Progetto side of the seeding.
    testImplementation(testFixtures(project(":progetto:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps
    // an expected `Esito.Ok` in the test.
    testImplementation(testFixtures(project(":kernel")))

    // AbbonatoDocumentoEventiTest: virtual time (StandardTestDispatcher/runTest, no real sleeps —
    // dev-architecture-app.md#test) to drive the coalescing/backoff coroutine deterministically.
    testImplementation(libs.kotlinx.coroutines.test)
}
