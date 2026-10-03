import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.testing.Test
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject

// llama-jni: a standalone JNI binding of llama.cpp. This script is self-contained on purpose: it reads
// nothing outside this directory (except the Gradle user home cache), so the directory can become an
// included build or its own repository without edits. Public plugins only.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
}

group = "io.github.lucolucus"
version = "0.1.0-SNAPSHOT"

kotlin {
    explicitApi()
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Runtime: the Kotlin stdlib only (added by the Kotlin plugin).
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("config/detekt.yml"))
    baseline = null
}

// CR-6 (no `!!`): `UnsafeCallOnNullableType` needs type resolution, which the plain `detekt` task lacks. The
// type-resolved `detektMain` runs that one rule only (config/detekt-cr6.yml, no default config) and joins `check`.
tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detektMain") {
    buildUponDefaultConfig = false
    config.setFrom(files("config/detekt-cr6.yml"))
}
tasks.named("check") { dependsOn("detektMain") }

// The unit tests run over a fake NativeBridge: no native library, no model, no C compiler.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("native")
    }
}

// --- Pins: llama.cpp release, per-OS asset, extracted libraries, test model ----------------------
// Set ONCE here. A release bump = new tag + new hashes + re-vendored headers (src/main/c/include/)
// + the shim re-checked against the new llama_*_params ABI.

val llamaRelease = "b11195"

/** One pinned release asset: the archive, its SHA-256, and archive member -> file name in the output. */
class NativeAsset(val url: String, val sha256: String, val libraries: Map<String, String>) {
    val fileName: String get() = url.substringAfterLast('/')
}

// Only macOS arm64 is wired (ADR 0027 Q-1). The member files are the versioned dylibs; each is written
// under the install name (`@rpath/lib*.0.dylib`) that libllama and the shim record, so no symlink is needed.
val nativeAssets: Map<String, NativeAsset> = mapOf(
    "macos-arm64" to NativeAsset(
        url = "https://github.com/ggml-org/llama.cpp/releases/download/$llamaRelease/llama-$llamaRelease-bin-macos-arm64.tar.gz",
        sha256 = "5320d5f90fde78fd046f78c2eab3e0cbde2ccd8b6aa4d3bcd6c15cbbb3672f98",
        libraries = mapOf(
            "libllama.0.5.0.dylib" to "libllama.0.dylib",
            "libggml.0.25.3.dylib" to "libggml.0.dylib",
            "libggml-base.0.25.3.dylib" to "libggml-base.0.dylib",
            "libggml-cpu.0.25.3.dylib" to "libggml-cpu.0.dylib",
            "libggml-blas.0.25.3.dylib" to "libggml-blas.0.dylib",
            "libggml-metal.0.25.3.dylib" to "libggml-metal.0.dylib",
            "libggml-rpc.0.25.3.dylib" to "libggml-rpc.0.dylib",
        ),
    ),
)

// Tiny public GGUF for the opt-in native tests: stories260K (Karpathy's tinyllamas, llama2.c), the model
// llama.cpp's own CI uses. Licence: MIT (huggingface.co/karpathy/tinyllamas). Pinned to a repository revision.
val testModel = NativeAsset(
    url = "https://huggingface.co/ggml-org/models/resolve/499bc8821c6b12b4e53c5bffcb21ec206f212d81/tinyllamas/stories260K.gguf",
    sha256 = "270cba1bd5109f42d03350f60406024560464db173c0e387d91f0426d3bd256d",
    libraries = emptyMap(),
)

val hostOsArch: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val system = when {
        "mac" in os -> "macos"
        "win" in os -> "windows"
        else -> "linux"
    }
    "$system-" + if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
}

// Outside the repository, shared by every checkout (ADR 0027 §3).
val downloadCache: File = gradle.gradleUserHomeDir.resolve("caches/llama-jni/$llamaRelease")
val nativeOutput = layout.buildDirectory.dir("natives/$hostOsArch")

fun notWired(task: String): Nothing = throw GradleException(
    "$task: host '$hostOsArch' is not wired — llama-jni builds its natives on macOS arm64 only for now " +
        "(ADR 0027 Q-1: Windows x64 / Linux x64 wait for their build host).",
)

/** Download-then-verify into the cache: a SHA-256 mismatch deletes the file and fails the task. */
fun downloadVerified(asset: NativeAsset, target: File, log: Logger) {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    if (target.isFile && sha256(target) == asset.sha256) return
    target.delete()
    target.parentFile.mkdirs()
    val partial = File(target.parentFile, "${target.name}.part")
    try {
        log.lifecycle("llama-jni: downloading ${asset.url}")
        val connection = URI(asset.url).toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val actual = sha256(partial)
        if (actual != asset.sha256) {
            throw GradleException(
                "llama-jni: SHA-256 mismatch for ${asset.url} — expected ${asset.sha256}, got $actual. " +
                    "The downloaded file was deleted.",
            )
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        partial.delete()
    }
}

/** Fetches the host's pinned llama.cpp asset into the cache and extracts ONLY the pinned libraries. */
abstract class DownloadLlamaNatives @Inject constructor(
    private val fs: FileSystemOperations,
    private val archives: ArchiveOperations,
) : DefaultTask() {
    @get:Internal lateinit var asset: () -> NativeAsset

    @get:Internal lateinit var fetch: (NativeAsset, File) -> Unit

    @get:Internal abstract val cache: DirectoryProperty

    @get:Input abstract val sha256: Property<String>

    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction
    fun extract() {
        val pinned = asset()
        val archive = cache.get().file(pinned.fileName).asFile
        fetch(pinned, archive)
        val dest = destination.get().asFile
        fs.delete { delete(dest) }
        fs.copy {
            from(archives.tarTree(archive))
            include { it.isDirectory || it.name in pinned.libraries }
            eachFile { relativePath = RelativePath(true, pinned.libraries.getValue(name)) }
            includeEmptyDirs = false
            into(dest)
        }
        val extracted = dest.list()?.toSet().orEmpty()
        if (extracted != pinned.libraries.values.toSet()) {
            fs.delete { delete(dest) }
            throw GradleException("downloadLlamaNatives: ${archive.name} yielded $extracted, expected ${pinned.libraries.values}")
        }
    }
}

/** Compiles the C11 shim against the vendored headers, the JDK's JNI headers and the extracted libllama. */
abstract class CompileJniShim @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory abstract val sources: DirectoryProperty

    @get:InputDirectory abstract val llamaLibraries: DirectoryProperty

    @get:Internal abstract val javaHome: DirectoryProperty

    @get:OutputFile abstract val shim: RegularFileProperty

    @TaskAction
    fun compile() {
        val src = sources.get().asFile
        val jdk = javaHome.get().asFile
        shim.get().asFile.parentFile.mkdirs()
        exec.exec {
            commandLine(
                "clang", "-std=c11", "-O2", "-Wall", "-Wextra", "-Werror", "-shared", "-fPIC",
                "-o", shim.get().asFile.absolutePath,
                File(src, "llamajni.c").absolutePath,
                "-I${File(src, "include").absolutePath}",
                "-I${File(jdk, "include").absolutePath}", "-I${File(jdk, "include/darwin").absolutePath}",
                File(llamaLibraries.get().asFile, "libllama.0.dylib").absolutePath,
                File(llamaLibraries.get().asFile, "libggml-base.0.dylib").absolutePath,
                File(llamaLibraries.get().asFile, "libggml.0.dylib").absolutePath,
                "-Wl,-rpath,@loader_path", "-Wl,-install_name,@rpath/libllamajni.dylib",
            )
        }
    }
}

val downloadLlamaNatives = tasks.register<DownloadLlamaNatives>("downloadLlamaNatives") {
    group = "llama-jni"
    description = "Fetches the pinned llama.cpp $llamaRelease asset for this host (SHA-256 verified, cached in the " +
        "Gradle user home) and extracts only the pinned libraries. Never part of check."
    val pinned = nativeAssets[hostOsArch]
    asset = { pinned ?: notWired("downloadLlamaNatives") }
    fetch = { a, f -> downloadVerified(a, f, logger) }
    cache.set(downloadCache)
    sha256.set(pinned?.sha256 ?: "unpinned")
    destination.set(layout.buildDirectory.dir("llama-$llamaRelease/$hostOsArch"))
}

val compileJniShim = tasks.register<CompileJniShim>("compileJniShim") {
    group = "llama-jni"
    description = "Compiles the JNI shim libllamajni for this host (macOS arm64: clang, rpath @loader_path). Never part of check."
    doFirst { if (hostOsArch !in nativeAssets) notWired("compileJniShim") }
    sources.set(layout.projectDirectory.dir("src/main/c"))
    llamaLibraries.set(downloadLlamaNatives.flatMap { it.destination })
    javaHome.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.map { it.metadata.installationPath })
    shim.set(layout.buildDirectory.file("shim/$hostOsArch/${System.mapLibraryName("llamajni")}"))
}

val assembleNatives = tasks.register<Sync>("assembleNatives") {
    group = "llama-jni"
    description = "Puts the shim and the llama.cpp libraries into build/natives/<os-arch>/, the library's one native output."
    doFirst { if (hostOsArch !in nativeAssets) notWired("assembleNatives") }
    from(downloadLlamaNatives.flatMap { it.destination })
    from(compileJniShim.flatMap { it.shim })
    into(nativeOutput)
}

val downloadTestModel = tasks.register("downloadTestModel") {
    group = "llama-jni"
    description = "Fetches the pinned tiny test GGUF (stories260K, MIT) into the Gradle user home cache."
    val target = downloadCache.resolve("models/${testModel.fileName}")
    outputs.file(target)
    doLast { downloadVerified(testModel, target, logger) }
}

// Opt-in: the real natives and a real (tiny) model. Outside the gate.
tasks.register<Test>("nativeTest") {
    group = "verification"
    description = "Opt-in: the library against the real llama.cpp natives and the pinned stories260K GGUF (tag native)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("native")
    }
    dependsOn(assembleNatives, downloadTestModel)
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ suite, result ->
            if (suite.parent == null) {
                val verdict = if (result.resultType == TestResult.ResultType.SUCCESS && result.testCount > 0) "PASS" else "FAIL"
                println("llama-jni nativeTest: $verdict (${result.successfulTestCount}/${result.testCount} tests)")
            }
        }),
    )
    systemProperty("llamajni.test.nativeDir", nativeOutput.get().asFile.absolutePath)
    systemProperty("llamajni.test.model", downloadCache.resolve("models/${testModel.fileName}").absolutePath)
}
