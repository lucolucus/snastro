plugins {
    id("snastro.kotlin-jvm")
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

    // Port Finte are kernel `Ripristinabile` (roll back with UnitaDiLavoroFinta); `atteso()` unwraps
    // an expected `Esito.Ok` in the test.
    testImplementation(testFixtures(project(":kernel")))
}
