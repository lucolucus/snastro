import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    base // root-level `check`/`build`/`clean` lifecycle so `verificaDipendenzeModuli` has a home.
}

// Root build: no domain code (wave-0 scaffold). Owns the project-wide gates that don't belong to
// a single module: the dependency-edge lint, and the opt-in tasks kept OUT of `check`.
// Sources: `.mismagent/architecture.md` (module map + allowed edges), `.mismagent/code-rules.md`
// (CR-1, CR-12), `.mismagent/infra-notes.md` (Needs -> work).

// --- verificaDipendenzeModuli -----------------------------------------------------------------
// Executable projection of architecture.md's "Allowed dependency edges" table. Anything not
// listed here is forbidden: an undeclared project-dependency edge fails the build.
val allProjectPaths: Set<String> = subprojects.map { it.path }.toSet()

val allowedModuleEdges: Map<String, Set<String>> = mapOf(
    // Intermediate path-holder projects (Gradle creates one per ':' segment of an `include(...)`
    // path, e.g. ":progetto" for ":progetto:dominio"); no build.gradle.kts, no dependencies.
    ":progetto" to emptySet(),
    ":trascrizione" to emptySet(),
    ":parlanti" to emptySet(),
    ":documento" to emptySet(),
    ":kernel" to emptySet(),
    ":progetto:dominio" to setOf(":kernel"),
    ":progetto:applicazione" to setOf(":progetto:dominio", ":kernel"),
    ":progetto:adattatori" to setOf(
        ":progetto:applicazione", ":progetto:dominio", ":kernel", ":persistenza", ":audio",
    ),
    ":trascrizione:dominio" to setOf(":kernel"),
    ":trascrizione:applicazione" to setOf(":trascrizione:dominio", ":kernel"),
    ":trascrizione:adattatori" to setOf(
        ":trascrizione:applicazione", ":trascrizione:dominio", ":kernel", ":persistenza",
        ":progetto:applicazione", ":audio", ":ml-sherpa",
    ),
    ":parlanti:dominio" to setOf(":kernel"),
    ":parlanti:applicazione" to setOf(":parlanti:dominio", ":kernel"),
    ":parlanti:adattatori" to setOf(
        ":parlanti:applicazione", ":parlanti:dominio", ":kernel", ":persistenza",
        ":progetto:applicazione", ":trascrizione:applicazione", ":audio", ":ml-sherpa",
    ),
    ":documento:applicazione" to setOf(":kernel"),
    ":documento:adattatori" to setOf(
        ":documento:applicazione", ":kernel", ":trascrizione:applicazione",
        ":parlanti:applicazione", ":progetto:applicazione",
    ),
    ":persistenza" to setOf(":kernel"),
    ":audio" to setOf(":kernel"),
    ":ml-sherpa" to setOf(":kernel", ":modelli"),
    ":modelli" to setOf(":kernel"),
    ":ui" to setOf(
        ":kernel", ":progetto:applicazione", ":trascrizione:applicazione",
        ":parlanti:applicazione", ":documento:applicazione",
    ),
    ":avvio" to (allProjectPaths - ":avvio"),
    ":architettura-test" to (allProjectPaths - ":architettura-test"),
)

tasks.register("verificaDipendenzeModuli") {
    group = "verification"
    description = "Fails if a project-to-project dependency edge is not in architecture.md's allowed-edges table."

    // Read-only introspection of already-configured subproject dependency declarations; must run
    // at execution time, after every subproject's build script has been evaluated (CR-1, CR-12).
    doLast {
        val violations = mutableListOf<String>()
        subprojects.forEach { sub ->
            val from = sub.path
            val allowed = allowedModuleEdges[from]
                ?: throw GradleException(
                    "verificaDipendenzeModuli: module '$from' is not listed in the allowed-edges table " +
                        "(build.gradle.kts) — add it or remove the module from settings.gradle.kts.",
                )
            sub.configurations.forEach { config ->
                config.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                    val to = dep.path
                    // `java-test-fixtures` wires an automatic self-dependency (a module's own
                    // `test` sourceSet seeing its own `testFixtures`) — reflexive, not a real edge.
                    if (to != from && to !in allowed) {
                        violations += "$from -> $to (configuration '${config.name}')"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "verificaDipendenzeModuli: undeclared module edge(s) — not in architecture.md:\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verificaDipendenzeModuli")
}


// --- sherpa-onnx: pinned GitHub release assets, SHA-256 verified (ADR 0016) ---------------------
// sherpa-onnx has NO Maven Central artifact (ADR 0016 §1). This block is the ONLY place of the
// pinned URLs and SHA-256 (AC-390); the version itself lives once in gradle/libs.versions.toml.
// A bump = catalog version + these rows + new hashes, recorded as an amendment of ADR 0016.
// Downloads are cached in the gitignored `native-cache/` at the repo root, which survives `clean`.

data class AssetSherpa(val url: String, val sha256: String) {
    val nomeFile: String get() = url.substringAfterLast('/')
}

val versioneSherpa: String = libs.versions.sherpa.onnx.get()
val cacheSherpa: Directory = layout.projectDirectory.dir("native-cache/sherpa-onnx-$versioneSherpa")

val jarSherpa = AssetSherpa(
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar",
    sha256 = "77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b",
)

// Host os-arch (k2-fsa asset naming) -> pinned `*-jni.tar.bz2`. Only macOS arm64 is proven and wired
// in v1; Windows/Linux names are documented in ADR 0016 §5, NOT wired here (AC-396).
val nativiSherpaPerHost: Map<String, AssetSherpa> = mapOf(
    "osx-arm64" to AssetSherpa(
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2",
        sha256 = "2505fd9bd46a28615ab3a14833caae8479c80993658402d57a949d7403c931ad",
    ),
)

(nativiSherpaPerHost.values + jarSherpa).forEach { asset ->
    check("/v$versioneSherpa/" in asset.url) {
        "sherpa-onnx asset ${asset.url} does not match the catalog version $versioneSherpa (ADR 0016 §1)"
    }
}

val hostSherpa: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val sistema = when {
        "mac" in os -> "osx"
        "win" in os -> "win"
        else -> "linux"
    }
    "$sistema-" + if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
}

// Compose `appResourcesRootDir` os-arch folder name (ADR 0016 §3): osx-arm64 -> macos-arm64, …
val cartellaRisorseHost: String = hostSherpa.replace("osx-", "macos-").replace("win-", "windows-")

/** Download-then-verify into a cache file: a SHA-256 mismatch deletes the file and fails (ADR 0016 §2). */
object ScaricamentoVerificato {
    private const val TIMEOUT_CONNESSIONE_MS = 30_000
    private const val TIMEOUT_LETTURA_MS = 120_000

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

    fun scarica(url: String, sha256Atteso: String, destinazione: File, logger: Logger) {
        if (destinazione.isFile && sha256(destinazione) == sha256Atteso) {
            logger.info("sherpa-onnx: cached, SHA-256 OK: ${destinazione.name}")
            return
        }
        destinazione.delete() // a stale/corrupt cache entry never survives
        destinazione.parentFile.mkdirs()
        val parziale = File(destinazione.parentFile, "${destinazione.name}.part")
        try {
            logger.lifecycle("sherpa-onnx: downloading $url")
            val connessione = URI(url).toURL().openConnection().apply {
                connectTimeout = TIMEOUT_CONNESSIONE_MS
                readTimeout = TIMEOUT_LETTURA_MS
            }
            connessione.getInputStream().use { input -> parziale.outputStream().use { input.copyTo(it) } }
            val effettivo = sha256(parziale)
            if (effettivo != sha256Atteso) {
                throw GradleException(
                    "sherpa-onnx: SHA-256 mismatch for $url — expected $sha256Atteso, got $effettivo. " +
                        "The downloaded file was deleted (ADR 0016 §2).",
                )
            }
            Files.move(parziale.toPath(), destinazione.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            parziale.delete()
        }
    }
}

/** The pure-Java sherpa-onnx API jar (`com.k2fsa.sherpa.onnx.*`) that `:ml-sherpa` compiles against. */
abstract class ScaricaJarSherpa : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputFile abstract val jar: RegularFileProperty

    @TaskAction
    fun scarica() = ScaricamentoVerificato.scarica(url.get(), sha256.get(), jar.get().asFile, logger)
}

/**
 * The host's JNI tarball -> ONLY the two libs, flat, into `<appResourcesRootDir>/<os-arch>/`
 * (ADR 0016 §2-3). The destination is emptied first, so a failed run (SHA mismatch, unpinned host,
 * unexpected archive) leaves nothing in it.
 */
abstract class ScaricaNativiSherpa @Inject constructor(
    private val fs: FileSystemOperations,
    private val archivi: ArchiveOperations,
) : DefaultTask() {
    @get:Input abstract val versione: Property<String>

    @get:Input abstract val host: Property<String>

    @get:Input @get:Optional abstract val url: Property<String>

    @get:Input @get:Optional abstract val sha256: Property<String>

    @get:Input abstract val librerie: ListProperty<String>

    @get:Internal abstract val cache: DirectoryProperty

    @get:OutputDirectory abstract val destinazione: DirectoryProperty

    @TaskAction
    fun scarica() {
        val dest = destinazione.get().asFile
        fs.delete { delete(dest) }
        val urlAsset = url.orNull ?: throw GradleException(
            "scaricaNativiSherpa: no sherpa-onnx native asset is pinned for host '${host.get()}' — " +
                "missing: sherpa-onnx-v${versione.get()}-${host.get()}-jni.tar.bz2 in ADR 0016 §1. Add its URL and " +
                "SHA-256 with an amendment of ADR 0016 (§5 documents the Windows/Linux asset names).",
        )
        val archivio = cache.get().file(urlAsset.substringAfterLast('/')).asFile
        ScaricamentoVerificato.scarica(urlAsset, sha256.get(), archivio, logger)

        val nomi = librerie.get().toSet()
        fs.copy {
            from(archivi.tarTree(archivi.bzip2(archivio)))
            include { it.isDirectory || it.name in nomi }
            eachFile { relativePath = RelativePath(true, name) }
            includeEmptyDirs = false
            into(dest)
        }
        val estratte = dest.list()?.toSet().orEmpty()
        if (estratte != nomi) {
            fs.delete { delete(dest) }
            throw GradleException("scaricaNativiSherpa: ${archivio.name} yielded $estratte, expected exactly $nomi")
        }
        logger.lifecycle("sherpa-onnx natives ready in $dest: $estratte")
    }
}

val scaricaJarSherpa = tasks.register<ScaricaJarSherpa>("scaricaJarSherpa") {
    group = "build setup"
    description = "Fetches the pinned sherpa-onnx JVM jar (SHA-256 verified) into native-cache/ (ADR 0016 §2). " +
        "Pure Java, no native code: the gate (:ml-sherpa compile) needs it."
    url.set(jarSherpa.url)
    sha256.set(jarSherpa.sha256)
    jar.set(cacheSherpa.file(jarSherpa.nomeFile))
}

val scaricaNativiSherpa = tasks.register<ScaricaNativiSherpa>("scaricaNativiSherpa") {
    group = "build setup"
    description = "Fetches the host's pinned sherpa-onnx JNI tarball (SHA-256 verified) and extracts only " +
        "onnxruntime + sherpa-onnx-jni into :avvio's appResourcesRootDir/<os-arch>/ (ADR 0016 §2-3). Never part of check."
    versione.set(versioneSherpa)
    host.set(hostSherpa)
    nativiSherpaPerHost[hostSherpa]?.let { asset ->
        url.set(asset.url)
        sha256.set(asset.sha256)
    }
    librerie.set(listOf(System.mapLibraryName("onnxruntime"), System.mapLibraryName("sherpa-onnx-jni")))
    cache.set(cacheSherpa)
    // :avvio's nativeDistributions.appResourcesRootDir is `build/risorse-app` (avvio/build.gradle.kts):
    // generated, never under src/, so natives can never be committed (ADR 0016 §3).
    destinazione.set(project(":avvio").layout.buildDirectory.dir("risorse-app/$cartellaRisorseHost"))
}

// --- Opt-in tasks (NEVER wired into `check`) --------------------------------------------------

tasks.register("modelliTest") {
    group = "verification"
    description = "Opt-in: real ML/audio adapter contract tests (@Tag(\"modelli\")) of every module that has a " +
        "modelliTest task; fetches the sherpa-onnx natives first (ADR 0004/0016)."
    dependsOn(scaricaNativiSherpa)
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("modelliTest") } })
}

tasks.register("benchmarkElaborazione") {
    group = "verification"
    description = "Opt-in NFR benchmark (ADR 0011, -Pcampione=<path to a 60-min sample>). Stub until esegui-elaborazione + real ML adapters."
    doLast {
        val campione = project.findProperty("campione") as String?
            ?: throw GradleException("benchmarkElaborazione needs -Pcampione=<path-to-60min-sample>")
        logger.lifecycle(
            "benchmarkElaborazione: stub (wave-0 scaffold) — sample '$campione' not processed yet. " +
                "Real timing arrives with the benchmark-elaborazione block (ADR 0011).",
        )
    }
}
