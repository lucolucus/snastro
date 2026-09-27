pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "snastro"

// Module map: `.mismagent/architecture.md`. Directory = Gradle project path
// (`progetto/dominio` <-> `:progetto:dominio`).
include(
    ":kernel",
    ":progetto:dominio",
    ":progetto:applicazione",
    ":progetto:adattatori",
    ":trascrizione:dominio",
    ":trascrizione:applicazione",
    ":trascrizione:adattatori",
    ":parlanti:dominio",
    ":parlanti:applicazione",
    ":parlanti:adattatori",
    ":documento:applicazione",
    ":documento:adattatori",
    // (2026-09-26, ADR 0021) :sintesi is a path-holder only (like :progetto, :trascrizione,
    // :parlanti, :documento): no build.gradle.kts, no dependencies of its own.
    ":sintesi:dominio",
    ":sintesi:applicazione",
    ":sintesi:adattatori",
    ":persistenza",
    ":audio",
    ":ml-sherpa",
    ":modelli",
    // (2026-09-26, ADR 0027) the standalone llama.cpp JNI library: no snastro dependency.
    ":llama-jni",
    ":ui",
    ":avvio",
    // (2026-09-27, ADR 0028) the domain-free technical libraries: no snastro dependency.
    ":supporto",
    ":supporto-test",
    ":architettura-test",
)
