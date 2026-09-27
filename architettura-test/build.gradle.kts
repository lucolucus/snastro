plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    testImplementation(libs.konsist)
}

// Konsist reads every module's sources (and CR-15 the build files) from disk at test time: declare
// them as inputs so the gate never reports this task UP-TO-DATE after another module changed.
// `.worktrees/` (i worktree git di altri rami, ignorati da git) non e questo albero: escluso da input e scansioni.
tasks.named<Test>("test") {
    inputs.files(
        fileTree(rootDir) {
            include("**/src/**/*.kt", "**/*.gradle.kts")
            exclude("**/build/**", ".gradle/**", "build-logic/.gradle/**", ".worktrees/**")
        },
    ).withPropertyName("sorgentiDelProgetto").withPathSensitivity(PathSensitivity.RELATIVE)

    // ControlliAdrTest runs the ADRs' enforced_by checks (controlli-adr/*.sh, `sh <script> <root>`) on
    // their fixtures and on the whole tree: the scripts, fixtures and ADRs, every tracked file the scans
    // read, and the integration markers that make a `from:` check applicable are all inputs.
    systemProperty("snastro.radiceProgetto", rootDir.absolutePath)
    inputs.dir("controlli-adr").withPropertyName("controlliAdr").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        fileTree(rootDir) {
            include(
                "**/src/**", "**/*.gradle.kts", "**/*.gradle", "**/*.toml", "**/*.py",
                ".mismagent/decisions/**", ".mismagent/features/*/integrated/**", ".mismagent/features/*/blocks/*/done/**",
            )
            exclude("**/build/**", ".gradle/**", "build-logic/.gradle/**", "native-cache/**", ".worktrees/**")
        },
    ).withPropertyName("alberoControllatoDagliAdr").withPathSensitivity(PathSensitivity.RELATIVE)
}
