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
}

compose.desktop {
    application {
        mainClass = "snastro.avvio.MainKt"
    }
}
