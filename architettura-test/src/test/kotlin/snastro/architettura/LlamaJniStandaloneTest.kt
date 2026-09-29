package snastro.architettura

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A160 (pre-release, llama-jni-libreria): `llama-jni/build.gradle.kts` applies its plugins by `id(...)`
 * with no version, relying on the enclosing snastro build having already resolved them (its root
 * `build.gradle.kts` declares them `apply false`, ADR 0027 §1's shared plugin classloader). That reliance
 * is invisible the moment `llama-jni/` is built on its own (`cd llama-jni && gradle build`) or pulled in as
 * a composite build (`includeBuild("llama-jni")`): Gradle then needs `llama-jni/`'s OWN settings file to
 * resolve the plugin versions and find a repository for them — ADR 0027 §1 promises exactly this move
 * needs "no edits inside it".
 */
class LlamaJniStandaloneTest {
    private val radice: File = File(System.getProperty("snastro.radiceProgetto") ?: "..").canonicalFile
    private val cartellaLlamaJni = File(radice, "llama-jni")
    private val settings = File(cartellaLlamaJni, "settings.gradle.kts")
    private val buildScript = File(cartellaLlamaJni, "build.gradle.kts")

    private val idPluginUsati: Set<String> by lazy {
        Regex("""id\("([^"]+)"\)""").findAll(buildScript.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `A160 llama-jni ha un settings gradle kts proprio`() {
        assertTrue(
            settings.isFile,
            "llama-jni/settings.gradle.kts is missing: the directory cannot be built standalone or as an " +
                "includeBuild without edits inside it (ADR 0027 §1)",
        )
    }

    @Test
    fun `A160 il settings gradle kts di llama-jni pin la versione di ogni plugin che build gradle kts chiede`() {
        assertTrue(idPluginUsati.size >= 2, "the guard must see llama-jni's own plugin ids: $idPluginUsati")
        val testo = settings.readText()
        assertTrue(
            Regex("""pluginManagement\s*\{""").containsMatchIn(testo),
            "llama-jni/settings.gradle.kts needs a pluginManagement block to resolve its plugins on its own",
        )
        assertTrue(
            Regex("""(mavenCentral|gradlePluginPortal)\s*\(\)""").containsMatchIn(testo),
            "llama-jni/settings.gradle.kts declares no plugin repository",
        )
        idPluginUsati.forEach { id ->
            assertTrue(
                Regex("""id\("${Regex.escape(id)}"\)\s+version\s+"[^"]+"""").containsMatchIn(testo),
                "llama-jni/settings.gradle.kts pins no version for plugin '$id', requested unversioned by " +
                    "llama-jni/build.gradle.kts",
            )
        }
    }
}
