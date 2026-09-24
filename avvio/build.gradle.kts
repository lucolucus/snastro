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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(testFixtures(project(":kernel")))
    testImplementation(testFixtures(project(":persistenza")))
    testImplementation(testFixtures(project(":progetto:applicazione")))
    testImplementation(testFixtures(project(":ui")))
    testImplementation(project(":progetto:dominio"))
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
