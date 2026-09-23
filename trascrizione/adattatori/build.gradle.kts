plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    // Allineatore (block port `tec-allineatore`) + RiconoscitoreParlato / Vad / Turno / SegmentoGrezzo
    // it is built on (`applicazione.porte`); kernel (CampioniAudio, IntervalloMs) reached transitively.
    implementation(project(":trascrizione:applicazione"))

    // AllineatoreContratto (AC-246) + RiconoscitoreParlatoFinta / VadFinta + the synthetic-signal
    // helpers (tonoDiProva/silenzio) — this adapter's own tests extend/build on the port's testFixtures
    // (dev-architecture-app.md#porta-contratto).
    testImplementation(testFixtures(project(":trascrizione:applicazione")))
}
