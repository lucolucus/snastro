import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.compose-desktop")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Headless offscreen rendering for `--smoke` (no display needed, full-agentic dev machine):
    // the only vetted Compose Desktop mechanism for this is the ui-test harness (JetBrains ships
    // no other public headless-capture API). Kept out of the packaged distribution is not possible
    // while `--smoke` lives in `main()`; accepted trade-off, reusing a vetted mechanism over a
    // hand-rolled `ComposeScene` driver (frugality rung 3).
    implementation(compose.desktop.uiTestJUnit4)

    // R0 composition root (avvio-r0): manual wiring of the whole R0 graph.
    implementation(project(":kernel"))
    implementation(project(":persistenza"))
    implementation(project(":audio"))
    implementation(project(":progetto:applicazione"))
    implementation(project(":progetto:adattatori"))
    implementation(project(":ui"))
    // RegistrazioniPresenter's optional `statiElaborazione`/`avviaElaborazione` parameters (both left
    // `null` in R0, AC-350) are typed over `:trascrizione:applicazione` — `:ui` depends on it only as
    // `implementation` (never `api`), so it is not on `:avvio`'s classpath transitively.
    implementation(project(":trascrizione:applicazione"))
    // RegistrazioniPresenter's optional `identificazioni` parameter (AC-204/AC-345, left `null` here —
    // R0/R1 show no badge) is typed over `:parlanti:applicazione`'s `ConteggioIdentificazione`, needed
    // for this call site to resolve even though the argument itself is omitted (same `implementation`,
    // not `api`, non-transitive reasoning as above). No `:parlanti` class is instantiated here — the
    // `avvio-parlanti` block wires the real `identificazioni` argument (AC-356 stays satisfied).
    implementation(project(":parlanti:applicazione"))

    // R1 composition (avvio-composizione, package snastro.avvio.r1): Trascrizione SQL repositories +
    // decoder + AllineatorePerTurno, Documento regeneration, :modelli behind ServizioModelli (AC-329),
    // MotoreSherpa handed to the real ML adapters once they wire themselves in (SelezioneAdattatoriMl).
    implementation(project(":trascrizione:adattatori"))
    implementation(project(":documento:applicazione"))
    implementation(project(":documento:adattatori"))
    implementation(project(":modelli"))
    implementation(project(":ml-sherpa"))
    // The ML Finte (DiarizzatoreFinta / RiconoscitoreParlatoFinta / VadFinta) are the pipeline's
    // adapters until diarizzatore-sherpa / riconoscitore-sherpa / vad-silero wire themselves into
    // SelezioneAdattatoriMl ("Finte until the ML blocks land", manifest) — and stay the forced choice of
    // `-Dsnastro.ml=finte` (the --smoke run: headless, no natives, no models).
    implementation(testFixtures(project(":trascrizione:applicazione")))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(testFixtures(project(":kernel")))
    testImplementation(testFixtures(project(":persistenza")))
    testImplementation(testFixtures(project(":progetto:applicazione")))
    testImplementation(testFixtures(project(":ui")))
    testImplementation(project(":progetto:dominio"))
    testImplementation(libs.kotlinx.coroutines.test) // CodaElaborazioniTest: StandardTestDispatcher (dev-architecture #dipendenze-test)
}

compose.desktop {
    application {
        mainClass = "snastro.avvio.MainKt"
        nativeDistributions {
            // ADR 0016 §3: generated under build/ (never src/), filled by the root task
            // scaricaNativiSherpa (<this dir>/<os-arch>/ = the two sherpa-onnx libs). At runtime Compose
            // exposes the merged folder as `compose.application.resources.dir` (MotoreSherpa.caricaNativi).
            appResourcesRootDir.set(layout.buildDirectory.dir("risorse-app"))
        }
    }
}

// ADR 0016 §2: the natives are fetched for run / distribution only — never for `check`. Compose's
// validation also requires this edge for prepareAppResources, which reads appResourcesRootDir.
tasks.matching { it.name in setOf("run", "createDistributable", "prepareAppResources") }.configureEach {
    dependsOn(":scaricaNativiSherpa")
}

// Opt-in (@Tag("modelli"), never in `check`): the R1 composition end to end over the REAL sherpa-onnx
// adapters and real FFmpeg (TrascrizioneRealeR1Test, models from SNASTRO_MODELLI_R1_DIR — never
// committed) plus the R0 real-FFmpeg tests. Aggregated by the root `modelliTest`. Natives as in
// :ml-sherpa's modelliTest: fetched first, `sherpa_onnx.native.path` pointing at them (ADR 0016 §4).
val scaricaNativiSherpa = rootProject.tasks.named("scaricaNativiSherpa")
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: R1 composition over the real ML adapters + real-FFmpeg tests (@Tag(\"modelli\"))."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    dependsOn(scaricaNativiSherpa)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dsherpa_onnx.native.path=${scaricaNativiSherpa.get().outputs.files.singleFile.absolutePath}")
    }
}
