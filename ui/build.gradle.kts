import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.compose-desktop")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(compose.desktop.uiTestJUnit4)
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
