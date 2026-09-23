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
    ":persistenza",
    ":audio",
    ":ml-sherpa",
    ":modelli",
    ":ui",
    ":avvio",
    ":architettura-test",
)
