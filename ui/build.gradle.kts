import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.compose-desktop")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":kernel"))
    implementation(libs.kotlinx.coroutines.core)

    // MessaggiErrore (AC-180): ErroreApplicazioneProgetto lives in `..applicazione.porte`; each
    // context's dominio-owned Errore<Contesto> is reachable transitively (fix-batch-10, CR-1
    // amendment) because `*:applicazione` exposes its own `*:dominio` as `api` — `:ui` never
    // declares a direct `*:dominio` dependency (code-rules.md CR-1(b)).
    implementation(project(":progetto:applicazione"))
    implementation(project(":trascrizione:applicazione"))
    implementation(project(":parlanti:applicazione"))

    testImplementation(compose.desktop.uiTestJUnit4)
    // RegistroProgettiFinta — ElencoProgetti's own dependency fake (ProgettiPresenterTest, AC-192/193/198).
    testImplementation(testFixtures(project(":progetto:applicazione")))

    // GeneratoreIdFinto + Esito test helpers (atteso/erroreAtteso), used by testFixtures (SessioneProgettoFinta) and tests alike.
    testFixturesApi(testFixtures(project(":kernel")))
    // StateFlow in SessioneProgettoFinta/-Contratto (not inherited from main's `implementation`).
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    // The Compose compiler plugin runs on every source set of this module; testFixtures needs the
    // runtime on its classpath even though it declares no `@Composable` (build-logic snastro.compose-desktop).
    testFixturesImplementation(compose.desktop.currentOs)
}

// Compose Desktop headless render-check (profile `ui_render_check`): every screen rendered
// offscreen at 1280x800 and 1024x640, PNGs to build/render-check/. Part of `check`.
val renderCheck by tasks.registering(Test::class) {
    description = "Headless Compose Desktop render-check (sizing/overflow/contrast/states)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("render")
    }
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(renderCheck)
}
