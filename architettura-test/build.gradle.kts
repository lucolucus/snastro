plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    testImplementation(libs.konsist)
}

// Konsist reads every module's sources (and CR-15 the build files) from disk at test time: declare
// them as inputs so the gate never reports this task UP-TO-DATE after another module changed.
tasks.named<Test>("test") {
    inputs.files(
        fileTree(rootDir) {
            include("**/src/**/*.kt", "**/*.gradle.kts")
            exclude("**/build/**", ".gradle/**", "build-logic/.gradle/**")
        },
    ).withPropertyName("sorgentiDelProgetto").withPathSensitivity(PathSensitivity.RELATIVE)
}
