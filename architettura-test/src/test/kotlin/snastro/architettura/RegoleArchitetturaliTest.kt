package snastro.architettura

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.interfaces
import com.lemonappdev.konsist.api.ext.list.objects
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * Executable projection of `code-rules.md`'s mechanical rules CR-1..CR-5, CR-8, CR-10, CR-14..CR-17
 * (the gate lint). Vacuously green at wave 0 (no domain code yet, `strict = false` default on every
 * Konsist assertion) — real coverage begins the moment an owner block adds source under a module.
 * CR-6, CR-7, CR-9, CR-11 are detekt/compiler jobs (`build-logic`); CR-12, CR-13 are
 * `verificaDipendenzeModuli` / `verifySqlDelightMigration` (root `build.gradle.kts`).
 */
class RegoleArchitetturaliTest {
    private val contesti = setOf("progetto", "trascrizione", "parlanti", "documento")

    private fun contestoDi(pacchetto: String): String? {
        val segmenti = pacchetto.removePrefix("snastro.").split(".")
        return segmenti.firstOrNull()?.takeIf { it in contesti }
    }

    private fun pacchettoImport(nomeImport: String): String = nomeImport.substringBeforeLast('.')

    /** This test file itself necessarily mentions the forbidden patterns it checks for (as string
     * literals/regexes) — exclude `:architettura-test`'s own sources from the text-based scans. */
    private fun KoFileDeclaration.isRegolaArchitetturale(): Boolean = packagee?.name == "snastro.architettura"

    private fun KoFileDeclaration.isStratoInterno(): Boolean {
        val pkg = packagee?.name ?: return false
        return pkg == "snastro.kernel" ||
            pkg.startsWith("snastro.kernel.") ||
            Regex("""^snastro\.[a-z]+\.dominio(\..+)?$""").matches(pkg) ||
            Regex("""^snastro\.[a-z]+\.applicazione(\..+)?$""").matches(pkg)
    }

    // --- CR-1 - Dependency rule --------------------------------------------------------------

    @Test
    fun `CR-1 nessun contesto importa il dominio o gli adattatori di un altro contesto`() {
        Konsist.scopeFromProject()
            .files
            .assertTrue { file ->
                val pkgFile = file.packagee?.name ?: return@assertTrue true
                val contestoFile = contestoDi(pkgFile)
                file.imports.all { imp ->
                    val pkgImport = pacchettoImport(imp.name)
                    val contestoImport = contestoDi(pkgImport)
                    val toccaDominioOAdattatori = pkgImport.endsWith(".dominio") || pkgImport.contains(".dominio.") ||
                        pkgImport.endsWith(".adattatori") || pkgImport.contains(".adattatori.")
                    contestoImport == null || contestoFile == null || contestoImport == contestoFile ||
                        !toccaDominioOAdattatori
                }
            }
    }

    @Test
    fun `CR-1 ui importa solo kernel e i moduli applicazione`() {
        Konsist.scopeFromProject()
            .files
            .filter { file ->
                val pkg = file.packagee?.name ?: return@filter false
                pkg == "snastro.ui" || pkg.startsWith("snastro.ui.")
            }
            .assertTrue { file ->
                file.imports
                    .filter { it.name.startsWith("snastro.") }
                    .all { imp -> imp.name.startsWith("snastro.kernel") || imp.name.contains(".applicazione") }
            }
    }

    // --- CR-2 - Inner modules are pure --------------------------------------------------------

    @Test
    fun `CR-2 kernel dominio e applicazione non importano framework, IO, persistenza, UI, ML o rete`() {
        val importVietati = listOf(
            "java.sql.", "javax.sql.", "javax.sound.", "java.net.", "java.nio.file.", "java.io.File",
            "app.cash.sqldelight", "org.sqlite", "androidx.compose", "org.jetbrains.compose",
            "com.k2fsa", "org.bytedeco", "io.ktor", "okhttp3",
        )
        Konsist.scopeFromProject()
            .files
            .filter { it.isStratoInterno() }
            .assertTrue { file -> file.imports.none { imp -> importVietati.any { imp.name.startsWith(it) } } }
    }

    // --- CR-3 - Technical confinement ---------------------------------------------------------

    @Test
    fun `CR-3 gli import tecnici restano confinati al proprio modulo`() {
        val regole: List<Pair<List<String>, (String) -> Boolean>> = listOf(
            listOf("com.k2fsa") to { pkg: String -> pkg == "snastro.ml" || pkg.startsWith("snastro.ml.") },
            listOf("org.bytedeco", "javax.sound") to { pkg: String ->
                pkg == "snastro.audio" || pkg.startsWith("snastro.audio.")
            },
            listOf("app.cash.sqldelight", "org.sqlite", "java.sql.", "javax.sql.") to { pkg: String ->
                pkg == "snastro.persistenza" || pkg.startsWith("snastro.persistenza.") ||
                    Regex("""^snastro\.(progetto|trascrizione|parlanti)\.adattatori(\..+)?$""").matches(pkg)
            },
            listOf("java.net.", "io.ktor", "okhttp3") to { pkg: String ->
                pkg == "snastro.modelli" || pkg.startsWith("snastro.modelli.")
            },
        )
        Konsist.scopeFromProject()
            .files
            .assertTrue { file ->
                val pkg = file.packagee?.name ?: return@assertTrue true
                regole.all { (prefissi, consentito) ->
                    consentito(pkg) || file.imports.none { imp -> prefissi.any { imp.name.startsWith(it) } }
                }
            }
    }

    // --- CR-4 - Aggregates are encapsulated, never `data class` -----------------------------

    @Test
    fun `CR-4 le classi del dominio non sono data class`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { it.resideInPackage("..dominio..") }
            .assertFalse { it.hasDataModifier }
    }

    @Test
    fun `CR-4 il dominio non espone proprieta var pubbliche`() {
        Konsist.scopeFromProject()
            .properties()
            .filter { it.resideInPackage("..dominio..") }
            .assertFalse { it.isVar && it.hasPublicOrDefaultModifier }
    }

    // --- CR-5 - Values are immutable ----------------------------------------------------------

    @Test
    fun `CR-5 nessuna data class espone una proprieta array grezza`() {
        val tipiArray = setOf(
            "Array", "FloatArray", "ByteArray", "IntArray", "DoubleArray", "LongArray", "ShortArray",
            "CharArray", "BooleanArray",
        )
        Konsist.scopeFromProject()
            .classes()
            .filter { it.hasDataModifier }
            .flatMap { it.properties() }
            .assertFalse { prop -> tipiArray.contains(prop.type?.name) }
    }

    @Test
    fun `CR-5 nessuna data class espone una proprieta var`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { it.hasDataModifier }
            .flatMap { it.properties() }
            .assertFalse { it.isVar }
    }

    // --- CR-8 - Expected failures are values ---------------------------------------------------

    private val nomeGerarchiaErrori = Regex("^Errore[A-Z][A-Za-z0-9]*$")
    private val nomeThrowable = Regex("^(Throwable|[A-Za-z0-9]*(Exception|Error))$")

    private fun nomeSemplice(nome: String): String = nome.substringBefore('<').substringAfterLast('.').trim()

    /** (name, direct parent names, direct + indirect parent names) of every class, object and interface. */
    private fun tipiConGenitori(): List<Triple<String, List<String>, List<String>>> {
        val scope = Konsist.scopeFromProject()
        fun riga(nome: String, diretti: List<KoParentDeclaration>, tutti: List<KoParentDeclaration>) =
            Triple(nome, diretti.map { nomeSemplice(it.name) }, tutti.map { nomeSemplice(it.name) })
        return scope.classes().map { riga(it.name, it.parents(), it.parents(indirectParents = true)) } +
            scope.objects().map { riga(it.name, it.parents(), it.parents(indirectParents = true)) } +
            scope.interfaces().map { riga(it.name, it.parents(), it.parents(indirectParents = true)) }
    }

    @Test
    fun `CR-8 nessun tipo errore estende Throwable`() {
        val violazioni = tipiConGenitori()
            .filter { (_, _, tutti) -> tutti.any { it == "ErroreDominio" || nomeGerarchiaErrori.matches(it) } }
            .filter { (_, _, tutti) -> tutti.any { nomeThrowable.matches(it) } }
            .map { it.first }
        kotlin.test.assertTrue(violazioni.isEmpty(), "Errori che estendono Throwable (CR-8): $violazioni")
    }

    @Test
    fun `CR-8 ogni sottotipo diretto di ErroreDominio e un interfaccia sealed Errore-Contesto`() {
        val scope = Konsist.scopeFromProject()
        fun diretto(genitori: List<KoParentDeclaration>) = genitori.any { nomeSemplice(it.name) == "ErroreDominio" }
        val classiDirette = scope.classes().filter { diretto(it.parents()) }.map { it.name }
        val oggettiDiretti = scope.objects().filter { diretto(it.parents()) }.map { it.name }
        val interfacceNonConformi = scope.interfaces()
            .filter { diretto(it.parents()) }
            .filterNot { it.hasSealedModifier && nomeGerarchiaErrori.matches(it.name) }
            .map { it.name }
        val violazioni = classiDirette + oggettiDiretti + interfacceNonConformi
        kotlin.test.assertTrue(violazioni.isEmpty(), "Sottotipi diretti non conformi (CR-8): $violazioni")
    }

    // --- CR-10 - Ubiquitous-language names (K6) -----------------------------------------------

    private val sinonimiVietati = setOf(
        "Speaker", "Cluster", "Transcript", "Job", "Utterance", "Chunk", "Embedding", "Voiceprint",
        "Score", "Confidenza", "Merge", "Mapping", "Workspace", "Meeting",
    )

    private fun isCanonicalPackage(resideInPackage: (String) -> Boolean): Boolean =
        resideInPackage("..dominio..") || resideInPackage("..applicazione..") || resideInPackage("..ui..")

    /** Names only (not the declarations): sidesteps the lack of Kotlin intersection types across
     * the five unrelated Konsist declaration interfaces (classes/interfaces/objects/functions/properties). */
    private fun nomiDichiarazioniCanoniche(): List<String> {
        val scope = Konsist.scopeFromProject()
        return buildList {
            addAll(scope.classes().filter { isCanonicalPackage(it::resideInPackage) }.map { it.name })
            addAll(scope.interfaces().filter { isCanonicalPackage(it::resideInPackage) }.map { it.name })
            addAll(scope.objects().filter { isCanonicalPackage(it::resideInPackage) }.map { it.name })
            addAll(scope.functions().filter { isCanonicalPackage(it::resideInPackage) }.map { it.name })
            addAll(scope.properties().filter { isCanonicalPackage(it::resideInPackage) }.map { it.name })
        }
    }

    @Test
    fun `CR-10 le dichiarazioni canoniche sono ASCII`() {
        val nonAscii = nomiDichiarazioniCanoniche().filter { nome -> nome.any { ch -> ch.code !in 32..126 } }
        kotlin.test.assertTrue(nonAscii.isEmpty(), "Nomi non ASCII (CR-10): $nonAscii")
    }

    @Test
    fun `CR-10 le dichiarazioni canoniche evitano i sinonimi del context-map`() {
        val vietati = nomiDichiarazioniCanoniche().filter { it in sinonimiVietati }
        kotlin.test.assertTrue(vietati.isEmpty(), "Sinonimi vietati usati (CR-10): $vietati")
    }

    // --- CR-14 - No wall clock in the inner layers ---------------------------------------------

    @Test
    fun `CR-14 dominio e applicazione non leggono l orologio di sistema direttamente`() {
        val pattern = listOf(
            Regex("""\bInstant\.now\("""),
            Regex("""\bLocalDate\.now\("""),
            Regex("""\bLocalDateTime\.now\("""),
            Regex("""\bZonedDateTime\.now\("""),
            Regex("""\bClock\.systemDefaultZone\("""),
            Regex("""\bClock\.systemUTC\("""),
            Regex("""\bSystem\.currentTimeMillis\("""),
            Regex("""\bSystem\.nanoTime\("""),
        )
        Konsist.scopeFromProject()
            .files
            .filter { it.isStratoInterno() }
            .assertTrue { file -> pattern.none { it.containsMatchIn(file.text) } }
    }

    // --- CR-15 - Reconstitution only from persistence adapters ----------------------------------

    private fun KoFileDeclaration.isAdattatorePersistenza(): Boolean =
        packagee?.name?.contains(".adattatori.persistenza") == true

    private fun KoFileDeclaration.isDominio(): Boolean =
        Regex("""^snastro\.[a-z]+\.dominio(\..+)?$""").matches(packagee?.name.orEmpty())

    private fun KoFileDeclaration.isDichiarazioneRicostituzione(): Boolean =
        path.replace('\\', '/').endsWith("kernel/src/main/kotlin/snastro/kernel/RicostituzioneDaPersistenza.kt")

    /** Source text without comments (a KDoc mention is not a use). */
    private fun KoFileDeclaration.codice(): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")

    private val optInRicostituzione =
        Regex("""OptIn\s*\((?:[^()]|\([^()]*\))*(?:\([^()]*)?RicostituzioneDaPersistenza""")
    private val aliasRicostituzione = Regex(
        """import\s+snastro\.kernel\.RicostituzioneDaPersistenza\s+as\s""" +
            """|typealias\s+\w+\s*=\s*(snastro\.kernel\.)?RicostituzioneDaPersistenza\b""",
    )
    private val importRicostituzione = Regex("""import\s+snastro\.kernel\.RicostituzioneDaPersistenza\s*\n""")
    private val marcaRicostituisci = Regex(
        """@(snastro\.kernel\.)?RicostituzioneDaPersistenza\s+""" +
            """(?:@\w+\s+|(?:public|internal|protected|private)\s+)*fun\s+ricostituisci\b""",
    )

    /** Opting in (any form: FQN, multi-marker, markerClass, @file:) only in persistence adapters. */
    @Test
    fun `CR-15 RicostituzioneDaPersistenza compare solo negli adattatori di persistenza`() {
        Konsist.scopeFromProject()
            .files
            .filter { !it.isRegolaArchitetturale() && optInRicostituzione.containsMatchIn(it.codice()) }
            .assertTrue { it.isAdattatorePersistenza() }
    }

    @Test
    fun `CR-15 RicostituzioneDaPersistenza non si aggira con alias o opzioni del compilatore`() {
        Konsist.scopeFromProject()
            .files
            .filter { !it.isRegolaArchitetturale() }
            .assertFalse { aliasRicostituzione.containsMatchIn(it.codice()) }
        val radice = java.io.File(System.getProperty("user.dir")).parentFile
        val buildConOptIn = radice.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", ".mismagent") }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .filter { it.readText().contains("RicostituzioneDaPersistenza") }
            .toList()
        kotlin.test.assertTrue(buildConOptIn.isEmpty(), "Opt-in nei build file (CR-15): $buildConOptIn")
    }

    /** Outside persistence adapters and its declaration, the marker only MARKS `dominio` `fun ricostituisci`. */
    @Test
    fun `CR-15 l annotazione RicostituzioneDaPersistenza marca solo i ricostituisci del dominio`() {
        Konsist.scopeFromProject()
            .files
            .filterNot { it.isRegolaArchitetturale() || it.isAdattatorePersistenza() }
            .filterNot { it.isDichiarazioneRicostituzione() }
            .assertFalse { file ->
                val codice = file.codice()
                val residuo = if (file.isDominio()) {
                    codice.replace(importRicostituzione, "").replace(marcaRicostituisci, "")
                } else {
                    codice
                }
                residuo.contains("RicostituzioneDaPersistenza")
            }
    }

    @Test
    fun `CR-15 ogni ricostituisci del dominio porta RicostituzioneDaPersistenza`() {
        Konsist.scopeFromProject()
            .functions()
            .filter { it.name == "ricostituisci" && it.resideInPackage("..dominio..") }
            .assertTrue { f -> f.annotations.any { nomeSemplice(it.name) == "RicostituzioneDaPersistenza" } }
    }

    // --- CR-16 - Command services expose only `esegui` ------------------------------------------

    @Test
    fun `CR-16 i servizi comando espongono solo esegui e ritornano Esito`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { it.resideInPackage("..applicazione.comandi..") && it.hasNameEndingWith("Servizio") }
            .assertTrue { servizio ->
                val pubbliche = servizio.functions().filter { it.hasPublicOrDefaultModifier }
                pubbliche.size == 1 &&
                    pubbliche.first().name == "esegui" &&
                    !pubbliche.first().hasSuspendModifier &&
                    pubbliche.first().returnType?.name == "Esito"
            }
    }

    // --- CR-17 - MockK scope ---------------------------------------------------------------------

    @Test
    fun `CR-17 nessun import mockk in testFixtures`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("src${'/'}testFixtures${'/'}") }
            .assertTrue { file -> file.imports.none { it.name.startsWith("io.mockk") } }
    }

    @Test
    fun `CR-17 nessun import mockk nelle classi Contratto`() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.classes().any { it.hasNameEndingWith("Contratto") } }
            .assertTrue { file -> file.imports.none { it.name.startsWith("io.mockk") } }
    }

    @Test
    fun `CR-17 nessun mockkStatic o mockkObject in nessun file`() {
        Konsist.scopeFromProject()
            .files
            .filter { !it.isRegolaArchitetturale() }
            .assertTrue { file -> !file.text.contains("mockkStatic(") && !file.text.contains("mockkObject(") }
    }
}
