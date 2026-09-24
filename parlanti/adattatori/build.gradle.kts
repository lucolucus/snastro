plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // ConfrontoImpronte (confronto-impronte) + ApplicaRevisionePolitica + RiallineaImpronteServizio,
    // consumed by AbbonatoRevisioneParlanti / AbbonatoRiallineamentoImpronte.
    implementation(project(":parlanti:applicazione"))

    // VociUnite/VoceDivisa/SegmentoRiassegnato (eventi-revisione Published Language), consumed by
    // AbbonatoRevisioneParlanti / AbbonatoRiallineamentoImpronte (CR-1: consumer:adattatori ->
    // supplier:applicazione only, never supplier:adattatori).
    implementation(project(":trascrizione:applicazione"))

    // AbbonatoRiallineamentoImpronte's background coalescing/retry coroutine (ADR 0012).
    implementation(libs.kotlinx.coroutines.core)

    // ConfrontoImpronteContratto (AC-127) + port Finte (ParlanteRepositoryFinta,
    // AttribuzioneRepositoryFinta, LettoreVociFinta, DecodificatoreAudioFinta, EstrattoreImprontaFinta)
    // — D1/D2 (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":parlanti:applicazione")))

    // Ripristinabile / UnitaDiLavoroFinta / ErroreDiProva / Esito test helpers.
    testImplementation(testFixtures(project(":kernel")))

    // AbbonatoRiallineamentoImpronteTest: virtual time (StandardTestDispatcher/runTest, no real
    // sleeps — dev-architecture-app.md#test) to drive the coalescing/backoff coroutine deterministically.
    testImplementation(libs.kotlinx.coroutines.test)
}
