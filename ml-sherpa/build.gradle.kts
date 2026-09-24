import org.gradle.api.tasks.testing.Test

plugins {
    id("snastro.kotlin-jvm")
}

// sherpa-onnx Java API (`com.k2fsa.sherpa.onnx.*`): NOT on Maven Central — the pinned GitHub release
// jar fetched and SHA-256 verified by the root task scaricaJarSherpa into native-cache/ (ADR 0016 §2).
// Pure Java: loading its classes loads no native code, so the gate stays native-free.
val scaricaJarSherpa = rootProject.tasks.named("scaricaJarSherpa")
val scaricaNativiSherpa = rootProject.tasks.named("scaricaNativiSherpa")

dependencies {
    implementation(files(scaricaJarSherpa.map { it.outputs.files }).builtBy(scaricaJarSherpa))
}

// Opt-in real native load (@Tag("modelli"), ADR 0004/0016): the natives are fetched first and
// `sherpa_onnx.native.path` points at <appResourcesRootDir>/<os-arch>/ (AC-243). One JVM per test
// class: a JVM loads the natives once, and each class proves one resolution path of caricaNativi().
tasks.register<Test>("modelliTest") {
    group = "verification"
    description = "Opt-in: MotoreSherpa against the real sherpa-onnx natives (@Tag(\"modelli\"), AC-243/244/398)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("modelli")
    }
    forkEvery = 1
    dependsOn(scaricaNativiSherpa)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dsherpa_onnx.native.path=${scaricaNativiSherpa.get().outputs.files.singleFile.absolutePath}")
    }
}
