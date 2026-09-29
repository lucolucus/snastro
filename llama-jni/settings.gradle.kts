// Makes ./llama-jni/ buildable standalone (`cd llama-jni && gradle build`) or as a composite build
// (`includeBuild("llama-jni")` from another Gradle build), with NO edits inside this directory (ADR 0027 §1:
// "laid out so that llama-jni/ can later become an included build, its own repository, or a published
// artifact, without edits inside it").
//
// Ignored by the enclosing snastro build: Gradle only reads the settings file of the directory it is
// invoked from (or the first one found walking UP), and a multi-project build never looks for a nested
// settings.gradle.kts inside a subproject directory. `./gradlew check` from the snastro root is unaffected.
//
// The plugin versions here MUST track llama-jni/build.gradle.kts's unversioned `id(...)` requests and
// gradle/libs.versions.toml's `kotlin` / `detekt` entries (checked by architettura-test's
// LlamaJniStandaloneTest, part of the gate).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.21"
        id("io.gitlab.arturbosch.detekt") version "1.23.8"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// No explicit root project name: Gradle already defaults it to the containing directory's name
// ("llama-jni"), and ADR 0027 §6 V2 forbids this file from naming `rootProject` at all.
