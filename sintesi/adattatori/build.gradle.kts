plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // LettoreTrascritto (the port this module implements) + kernel Published Language types reached
    // transitively (applicazione exposes :kernel as `api`).
    implementation(project(":sintesi:applicazione"))

    // VociDelTrascritto + StatiElaborazione (Trascrizione's public read API) — the whole supplier side
    // of LettoreTrascrittoDaTrascrizione (boundary trascritto-per-sintesi, ADR 0021 §3: consumer:adattatori
    // -> supplier:applicazione only, never supplier:adattatori or a generated *Queries type, AC-S52).
    implementation(project(":trascrizione:applicazione"))

    // LettoreTrascrittoContratto + AmbienteLettoreTrascritto + SemeTurno/SegmentoConiato (testFixtures) —
    // D2: this module's adapter test extends the port contract (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":sintesi:applicazione")))

    // Trascrizione's own commands (AvviaElaborazioneServizio, EseguiProssimaElaborazioneServizio,
    // AnnullaElaborazioneServizio, RiassegnaSegmentoServizio) + its port fakes (ElaborazioneRepositoryFinta,
    // TrascrittoRepositoryFinta, DecodificatoreAudioFinta, DiarizzatoreFinta, AllineatoreFinta,
    // SegnalatoreFaseFinta, LettoreRegistrazioneFinta) — D2 seeds the supplier only through ITS OWN
    // commands over its own in-memory fakes, never Trascrizione's SQL repositories (ADR 0002, CR-1:
    // consumer:adattatori reaches only supplier:applicazione, never supplier:adattatori).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps an
    // expected `Esito.Ok` in the test; GeneratoreIdFinto mints deterministic ids.
    testImplementation(testFixtures(project(":kernel")))
}
