plugins {
    id("snastro.kotlin-jvm")
}

// ADR 0005: bytedeco FFmpeg (LGPL build, never a `-gpl` artifact — AC-126) via javacv, confined to
// `:audio` (CR-3). "One per-OS classifier per target": only the classifier of the machine running
// Gradle is resolved (~20-40 MB, ADR 0005's own estimate), not every OS's natives; a build run on
// another OS resolves that OS's classifier the same way — no `ffmpeg-platform` fat aggregate.
fun classificatoreFfmpeg(): String {
    val os = System.getProperty("os.name").lowercase()
    val piattaforma = when {
        os.contains("mac") -> "macosx"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val arch = System.getProperty("os.arch").lowercase()
    val architettura = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
        else -> "x86_64"
    }
    return "$piattaforma-$architettura"
}

dependencies {
    implementation(libs.bytedeco.javacv)
    implementation(variantOf(libs.bytedeco.ffmpeg) { classifier(classificatoreFfmpeg()) })
}
