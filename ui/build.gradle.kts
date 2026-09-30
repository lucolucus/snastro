import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.compose-desktop")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":kernel"))
    implementation(libs.kotlinx.coroutines.core)
    // catturaNonFatale (ADR 0028 §5, CR-19b): the sanctioned catch-all at :ui's platform edges.
    implementation(project(":supporto"))

    // MessaggiErrore (AC-180): ErroreApplicazioneProgetto lives in `..applicazione.porte`; each
    // context's dominio-owned Errore<Contesto> is reachable transitively (fix-batch-10, CR-1
    // amendment) because `*:applicazione` exposes its own `*:dominio` as `api` — `:ui` never
    // declares a direct `*:dominio` dependency (code-rules.md CR-1(b)).
    implementation(project(":progetto:applicazione"))
    implementation(project(":trascrizione:applicazione"))
    implementation(project(":parlanti:applicazione"))
    // ADR 0021 §2 (`:ui` *(adds)* `:sintesi:applicazione`): RiassuntoVista/ImpostazioniSintesiVista,
    // the Riassumi/ModificaLunghezzaMassimaRiassunto commands, ErroreSintesi (CR-1(b), scheda-riassunto's
    // own boundary).
    implementation(project(":sintesi:applicazione"))

    testImplementation(compose.desktop.uiTestJUnit4)
    // RegistroProgettiFinta — ElencoProgetti's own dependency fake (ProgettiPresenterTest, AC-192/193/198).
    testImplementation(testFixtures(project(":progetto:applicazione")))
    // attendiFinche/conScopeDiProva (ADR 0028 §5, test-only edge): the one polling wait and the
    // cancelled-in-finally scope every real-thread presenter test in this module now goes through.
    testImplementation(project(":supporto-test"))

    // GeneratoreIdFinto + Esito test helpers (atteso/erroreAtteso), used by testFixtures (SessioneProgettoFinta) and tests alike.
    testFixturesApi(testFixtures(project(":kernel")))
    // StateFlow in SessioneProgettoFinta/-Contratto (not inherited from main's `implementation`).
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    // ComandiVoceContratto (runTest/virtual time) — the consumer-driven contract of the ComandiVoce port.
    testFixturesImplementation(libs.kotlinx.coroutines.test)
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
