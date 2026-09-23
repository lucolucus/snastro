import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared setup applied by every `snastro.*` convention plugin (avoids the chicken-and-egg problem
 * of one precompiled script plugin applying another from the same build-logic project).
 */

internal val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** Kotlin toolchain: JetBrains Runtime 21, warnings as errors (CR-9, ADR 0001). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.matching("JetBrains"))
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
}

/** detekt (+ formatting), no baseline, project-wide config (CR-6, CR-7, CR-11, code-rules.md). */
internal fun Project.configureDetekt() {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        ignoreFailures = false
        config.setFrom(files(rootDir.resolve("config/detekt/detekt.yml")))
        baseline = null
    }
    dependencies.add("detektPlugins", versionCatalog.library("detekt-formatting"))
}

/** JUnit 5 platform + kotlin.test assertions + coroutines-test + mockk (dev-architecture-app.md#test). */
internal fun Project.configureTesting() {
    val libs = versionCatalog
    dependencies.apply {
        add("testImplementation", libs.library("kotlin-test"))
        add("testImplementation", libs.library("kotlin-test-junit5"))
        add("testImplementation", libs.library("junit-jupiter"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("testImplementation", libs.library("mockk"))
        add("testRuntimeOnly", libs.library("junit-platform-launcher"))

        add("testFixturesImplementation", libs.library("kotlin-test"))
        add("testFixturesImplementation", libs.library("junit-jupiter"))
    }
    // Only the default `test` task excludes these tags — a custom Test task (e.g. `:ui:renderCheck`)
    // configures its own `useJUnitPlatform { includeTags(...) }` and must not inherit this filter
    // (the JUnit Platform Gradle engine excludes a tag that is both included and excluded).
    tasks.named<Test>("test").configure {
        useJUnitPlatform {
            // Real ML/audio adapters -> opt-in `modelliTest` (ADR 0004). Render checks -> the
            // dedicated `:ui:renderCheck` task.
            excludeTags("modelli", "render")
        }
    }
}
