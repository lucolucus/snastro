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
    // ModelloEmbeddingFinto (testFixtures) builds EmbeddingSherpa's fake loader, typed over sherpa's config.
    testFixturesImplementation(files(scaricaJarSherpa.map { it.outputs.files }).builtBy(scaricaJarSherpa))

    // CampioniAudio / IntervalloMs (Published Language, kernel-pl): RiconoscitoreSherpa's input/output
    // types, so sherpa's own types never cross tec-ml-sherpa's boundary (RC-3).
    implementation(project(":kernel"))

    // CartellaCacheModelli + the asr-parakeet-tdt-0.6b-v3-int8 catalogue id (ADR 0008/0013): where
    // RiconoscitoreSherpaModelliTest (@Tag("modelli")) finds the real model by default.
    implementation(project(":modelli"))
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
