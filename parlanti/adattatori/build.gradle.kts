plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ConfrontoImpronte (confronto-impronte) + ApplicaRevisionePolitica, consumed by
    // AbbonatoRevisioneParlanti (abbonato-revisione-parlanti).
    implementation(project(":parlanti:applicazione"))

    // VociUnite/VoceDivisa/SegmentoRiassegnato (eventi-revisione Published Language), consumed by
    // AbbonatoRevisioneParlanti (CR-1: consumer:adattatori -> supplier:applicazione only, never
    // supplier:adattatori).
    implementation(project(":trascrizione:applicazione"))

    // ConfrontoImpronteContratto (AC-127) + port Finte (ParlanteRepositoryFinta,
    // AttribuzioneRepositoryFinta) — D1/D2 (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Ripristinabile / UnitaDiLavoroFinta / ErroreDiProva / Esito test helpers.
    testImplementation(testFixtures(project(":kernel")))
}
