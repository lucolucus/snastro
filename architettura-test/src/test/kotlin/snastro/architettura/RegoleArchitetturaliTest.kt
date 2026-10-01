package snastro.architettura

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoParentDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.interfaces
import com.lemonappdev.konsist.api.ext.list.objects
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proiezione eseguibile delle regole meccaniche di `code-rules.md` CR-1..CR-5, CR-8, CR-10, CR-14..CR-17
 * (il lint del gate), su tutti i contesti (Progetto, Trascrizione, Parlanti, Sbobinatura, Sintesi) e sui
 * moduli tecnici; CR-18 sulle librerie `:supporto` / `:supporto-test` (ADR 0028). Lo scope e il progetto intero,
 * `.worktrees/` esclusa ([progetto]), letto una sola volta.
 * CR-6, CR-7, CR-9, CR-11 sono compiti di detekt e del compilatore (`build-logic`); CR-12 e
 * `verificaDipendenzeModuli` (`build.gradle.kts` di radice); CR-13 e il test di migrazione di
 * `:persistenza:test`. Gli script di `controlli-adr` degli ADR girano in [ControlliAdrTest].
 */
class RegoleArchitetturaliTest {
    private val contesti = setOf("progetto", "trascrizione", "parlanti", "sbobinatura", "sintesi")

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

    // --- ADR 0033 §1 (incontro-chiavi) - the Voce is keyed by its Incontro --------------------------------------

    @Test
    fun `AC-I11 VoceRef ha nel costruttore esattamente incontroId e voceId, nessun registrazioneId`() {
        val voceRef = progetto.classes().filter { it.name == "VoceRef" && it.packagee?.name == "snastro.kernel" }
        assertEquals(1, voceRef.size, "un solo VoceRef, in :kernel")
        val parametri = checkNotNull(
            voceRef.single().primaryConstructor,
        ).parameters.map { "${it.name}: ${it.type.text}" }
        assertEquals(listOf("incontroId: IncontroId", "voceId: VoceId"), parametri)
    }

    // --- CR-1 - Dependency rule --------------------------------------------------------------

    @Test
    fun `CR-1 nessun contesto importa il dominio o gli adattatori di un altro contesto`() {
        progetto
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

    /**
     * A context's `dominio`-owned error hierarchy, and only it: `Errore<Contesto>` itself or a
     * nested member (`ErroreProgetto.NomeProgettoVuoto`). No aggregate/VO of `dominio` matches
     * (ADR 0003 (b): `:ui`'s `MessaggiErrore` maps these hierarchies exhaustively, AC-180/RC-4).
     */
    private val gerarchiaErroreDominio = Regex("""^snastro\.[a-z]+\.dominio\.Errore[A-Z][A-Za-z]*(\..+)?$""")

    /** `:ui`'s own package, any depth — intra-`:ui` imports across screens/shared UI code. */
    private fun isPacchettoUi(nomeImport: String): Boolean =
        nomeImport == "snastro.ui" || nomeImport.startsWith("snastro.ui.")

    /** CR-1's allowance for a `snastro.*` import inside a `:ui` file: kernel, any `applicazione`
     * module, `:ui` itself, or a context's `dominio` error hierarchy (never any other `dominio` type).
     * (2026-09-27, ADR 0028 §5) also the domain-free `snastro.supporto..`; `snastro.supporto.test` stays out of
     * `src/main` and `src/testFixtures` by CR-18. */
    private fun importUiConsentito(nomeImport: String): Boolean =
        nomeImport.startsWith("snastro.kernel") ||
            nomeImport.contains(".applicazione") ||
            isPacchettoUi(nomeImport) ||
            gerarchiaErroreDominio.matches(nomeImport) ||
            nomeImport.startsWith("snastro.supporto.")

    @Test
    fun `CR-1 ui importa solo kernel, i moduli applicazione, se stesso e le gerarchie errore del dominio`() {
        progetto
            .files
            .filter { file ->
                val pkg = file.packagee?.name ?: return@filter false
                pkg == "snastro.ui" || pkg.startsWith("snastro.ui.")
            }
            .assertTrue { file ->
                file.imports
                    .filter { it.name.startsWith("snastro.") }
                    .all { imp -> importUiConsentito(imp.name) }
            }
    }

    /**
     * Direct unit test of the predicate above (no fixture files needed). Verifies BOTH directions stay
     * live: the two allowances accept their cases, and a `dominio` type that is NOT an error hierarchy
     * (e.g. an aggregate root) is still rejected — i.e. the rule can still go red.
     */
    @Test
    fun `CR-1 il predicato di importazione ui accetta le nuove eccezioni e rifiuta il resto del dominio`() {
        val consentiti = listOf(
            "snastro.kernel.Esito",
            "snastro.progetto.applicazione.porte.RegistroProgetti",
            "snastro.progetto.applicazione.porte.ErroriApplicazioneProgetto.CopiaFallita",
            "snastro.ui",
            "snastro.ui.testi.Colori",
            "snastro.ui.s1.SchermataUno",
            "snastro.progetto.dominio.ErroreProgetto",
            "snastro.progetto.dominio.ErroreProgetto.NomeProgettoVuoto",
            "snastro.trascrizione.dominio.ErroreTrascrizione",
            "snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato",
            "snastro.parlanti.dominio.ErroreParlanti.ParlanteNonTrovato",
            "snastro.supporto.RitentaConBackoff",
            "snastro.supporto.test.attendiFinche",
        )
        val vietati = listOf(
            "snastro.progetto.dominio.Progetto",
            "snastro.progetto.dominio.Registrazione",
            "snastro.trascrizione.dominio.Trascritto",
            "snastro.trascrizione.dominio.Elaborazione",
            "snastro.parlanti.dominio.Parlante",
            "snastro.progetto.adattatori.persistenza.RepositoryProgettoSqlite",
            "snastro.progetto.dominio.errore.QualcosaltroNonErrore",
            "snastro.supportoaltro.Qualcosa",
        )
        val accettatiPerErrore = consentiti.filterNot { importUiConsentito(it) }
        val rifiutatiPerErrore = vietati.filter { importUiConsentito(it) }
        kotlin.test.assertTrue(accettatiPerErrore.isEmpty(), "Import legittimi rifiutati: $accettatiPerErrore")
        kotlin.test.assertTrue(rifiutatiPerErrore.isEmpty(), "Import vietati accettati: $rifiutatiPerErrore")
    }

    // --- CR-2 - Inner modules are pure --------------------------------------------------------

    @Test
    fun `CR-2 kernel dominio e applicazione non importano framework, IO, persistenza, UI, ML o rete`() {
        val importVietati = listOf(
            "java.sql.", "javax.sql.", "javax.sound.", "java.net.", "java.nio.file.", "java.io.File",
            "app.cash.sqldelight", "org.sqlite", "androidx.compose", "org.jetbrains.compose",
            "com.k2fsa", "org.bytedeco", "io.ktor", "okhttp3",
        )
        progetto
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
                    Regex("""^snastro\.(progetto|trascrizione|parlanti|sintesi)\.adattatori(\..+)?$""").matches(pkg)
            },
            listOf("java.net.", "io.ktor", "okhttp3") to { pkg: String ->
                pkg == "snastro.modelli" || pkg.startsWith("snastro.modelli.")
            },
        )
        progetto
            .files
            .assertTrue { file ->
                val pkg = file.packagee?.name ?: return@assertTrue true
                regole.all { (prefissi, consentito) ->
                    consentito(pkg) || file.imports.none { imp -> prefissi.any { imp.name.startsWith(it) } }
                }
            }
    }

    // --- CR-3b - Transactions only through the kernel ports (ADR 0029) --------------------------

    /** SQLDelight's `transaction { }` / `transaction(...)` / `transactionWithResult { }` calls (member-call
     * syntax, the `.transaction`/`.transactionWithResult` receiver dot required — never a bare declaration). */
    private val chiamataTransazioneSql = Regex("""\.transaction(WithResult)?\s*[({]""")

    private fun KoFileDeclaration.isSrcMain(): Boolean = "/src/main/" in path.replace('\\', '/')

    private fun pacchettoPersistenza(pkg: String): Boolean =
        pkg == "snastro.persistenza" || pkg.startsWith("snastro.persistenza.")

    private fun KoFileDeclaration.isPersistenza(): Boolean = pacchettoPersistenza(packagee?.name.orEmpty())

    @Test
    fun `CR-3b transaction e transactionWithResult di SQLDelight si chiamano in src main solo dentro persistenza`() {
        progetto
            .files
            .filter { it.isSrcMain() }
            .assertTrue { file -> file.isPersistenza() || !chiamataTransazioneSql.containsMatchIn(file.codice()) }
    }

    /**
     * Direct unit test of the predicate above (CR-1's own pattern, line 110): a throwaway `db.transaction { }`
     * in a *:adattatori file (package `snastro.trascrizione.adattatori.persistenza`) or in `:avvio`
     * (`snastro.avvio`) would FAIL the rule above; the same call inside `snastro.persistenza` passes.
     */
    @Test
    fun `CR-3b un transaction fuori da persistenza in adattatori o avvio fallirebbe, persistenza passa`() {
        fun violerebbe(pacchetto: String, testo: String): Boolean =
            !pacchettoPersistenza(pacchetto) && chiamataTransazioneSql.containsMatchIn(testo)

        kotlin.test.assertTrue(violerebbe("snastro.trascrizione.adattatori.persistenza", "db.transaction { }"))
        kotlin.test.assertTrue(violerebbe("snastro.avvio", "db.transactionWithResult { 1 }"))
        kotlin.test.assertFalse(violerebbe("snastro.persistenza", "db.transaction { }"))
        kotlin.test.assertFalse(violerebbe("snastro.avvio", "qualcosaltro.chiamata { }"))
    }

    // --- CR-4 - Aggregates are encapsulated, never `data class` -----------------------------

    /**
     * The aggregate roots, verbatim from the features' tactical models (the `(root)` rows):
     * `.mismagent/features/trascrizione-con-parlanti/tactical-model.md` — Progetto, Registrazione (§ Progetto);
     * Elaborazione, Trascritto (§ Trascrizione); Parlante, Attribuzione (§ Parlanti); Sbobinatura has none —
     * and `.mismagent/features/sintesi/tactical-model.md` — Riassunto, LunghezzaMassimaRiassunto (§ Sintesi).
     * CR-4 bans `data class` for these only: VOs, events and error members MUST be `data class` (CR-5).
     * A feature adding a root amends this list.
     */
    private val radiciAggregato = setOf(
        "Progetto",
        "Registrazione",
        "Elaborazione",
        "Trascritto",
        "Parlante",
        "Attribuzione",
        "Riassunto",
        "LunghezzaMassimaRiassunto",
    )

    @Test
    fun `CR-4 le radici di aggregato del dominio non sono data class`() {
        progetto
            .classes()
            .filter { it.resideInPackage("..dominio..") && it.name in radiciAggregato }
            .assertFalse { it.hasDataModifier }
    }

    @Test
    fun `CR-4 il dominio non espone proprieta var pubbliche`() {
        progetto
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
        progetto
            .classes()
            .filter { it.hasDataModifier }
            .flatMap { it.properties() }
            .assertFalse { prop -> tipiArray.contains(prop.type?.name) }
    }

    @Test
    fun `CR-5 nessuna data class espone una proprieta var`() {
        progetto
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
        val scope = progetto
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
        val scope = progetto
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
        // (2026-09-25, ADR 0021) Sintesi's "Not:" terms (context-map.md § Sintesi), single-word
        // ones that could name a declaration; multi-word terms ("stato elaborazione", "cosa da
        // fare", …) cannot collide with a Kotlin identifier and are left out. Excluded on purpose:
        // "sintesi" (the context's own package name), "Contesto" (a real Parlanti class,
        // `letture/Proposta.kt`), "Proposta" and "Riferimento" (already legitimate terms elsewhere,
        // per the context-map's own parenthetical).
        "Summary", "Verbale", "Report", "Resoconto", "Minuta",
        // (2026-09-30, ADR 0031) the term before the rename to Sbobinatura.
        "Documento",
        "Sintetizza", "Rigenera", "Aggiorna",
        "Tema", "Prompt", "Descrizione", "Oggetto",
        "Rumore", "Divagazione",
        "Abstract",
        "Delibera", "Conclusione", "Accordo",
        "Ipotesi", "Pendenza",
        "Task", "Todo", "Compito",
        "Assegnatario", "Owner", "Incaricato",
        "Highlight", "Takeaway",
        "Citazione", "Source", "Prova",
        "Validazione", "Grounding",
        "Obsoleto", "Scaduto", "Stale", "Invalidato",
    )

    private fun isCanonicalPackage(resideInPackage: (String) -> Boolean): Boolean =
        resideInPackage("..dominio..") || resideInPackage("..applicazione..") || resideInPackage("..ui..")

    /** Names only (not the declarations): sidesteps the lack of Kotlin intersection types across
     * the five unrelated Konsist declaration interfaces (classes/interfaces/objects/functions/properties). */
    private fun nomiDichiarazioniCanoniche(): List<String> {
        val scope = progetto
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
        progetto
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

    /** The marker, then any other annotations (with arguments), modifiers and type parameters, then `ricostituisci`. */
    private val marcaRicostituisci = Regex(
        """@(snastro\.kernel\.)?RicostituzioneDaPersistenza\s+""" +
            """(?:@[\w.]+(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*""" +
            """|(?:public|internal|protected|private|inline|suspend|override|open|final|actual|external""" +
            """|tailrec)\s+)*""" +
            """fun\s+(?:<(?:[^<>]|<[^<>]*>)*>\s*)?ricostituisci\b""",
    )

    /** Opting in (any form: FQN, multi-marker, markerClass, @file:) only in persistence adapters. */
    @Test
    fun `CR-15 RicostituzioneDaPersistenza compare solo negli adattatori di persistenza`() {
        progetto
            .files
            .filter { !it.isRegolaArchitetturale() && optInRicostituzione.containsMatchIn(it.codice()) }
            .assertTrue { it.isAdattatorePersistenza() }
    }

    @Test
    fun `CR-15 RicostituzioneDaPersistenza non si aggira con alias o opzioni del compilatore`() {
        progetto
            .files
            .filter { !it.isRegolaArchitetturale() }
            .assertFalse { aliasRicostituzione.containsMatchIn(it.codice()) }
        val radice = java.io.File(System.getProperty("user.dir")).parentFile
        val buildConOptIn = radice.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", ".mismagent", ".worktrees") }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .filter { it.readText().contains("RicostituzioneDaPersistenza") }
            .toList()
        kotlin.test.assertTrue(buildConOptIn.isEmpty(), "Opt-in nei build file (CR-15): $buildConOptIn")
    }

    /** Outside persistence adapters and its declaration, the marker only MARKS `dominio` `fun ricostituisci`. */
    @Test
    fun `CR-15 l annotazione RicostituzioneDaPersistenza marca solo i ricostituisci del dominio`() {
        progetto
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
        progetto
            .functions()
            .filter { it.name == "ricostituisci" && it.resideInPackage("..dominio..") }
            .assertTrue { f -> f.annotations.any { nomeSemplice(it.name) == "RicostituzioneDaPersistenza" } }
    }

    // --- CR-16 - Command services expose only `esegui` ------------------------------------------

    @Test
    fun `CR-16 i servizi comando espongono solo esegui e ritornano Esito`() {
        progetto
            .classes()
            .filter { it.resideInPackage("..applicazione.comandi..") && it.hasNameEndingWith("Servizio") }
            .assertTrue { servizio ->
                val pubbliche = servizio.functions().filter { it.hasPublicOrDefaultModifier }
                pubbliche.size == 1 &&
                    pubbliche.first().name == "esegui" &&
                    !pubbliche.first().hasSuspendModifier &&
                    pubbliche.first().returnType?.name?.substringBefore('<') == "Esito"
            }
    }

    // --- CR-17 - MockK scope ---------------------------------------------------------------------

    @Test
    fun `CR-17 nessun import mockk in testFixtures`() {
        progetto
            .files
            .filter { it.path.contains("src${'/'}testFixtures${'/'}") }
            .assertTrue { file -> file.imports.none { it.name.startsWith("io.mockk") } }
    }

    @Test
    fun `CR-17 nessun import mockk nelle classi Contratto`() {
        progetto
            .files
            .filter { file -> file.classes().any { it.hasNameEndingWith("Contratto") } }
            .assertTrue { file -> file.imports.none { it.name.startsWith("io.mockk") } }
    }

    @Test
    fun `CR-17 nessun mockkStatic o mockkObject in nessun file`() {
        progetto
            .files
            .filter { !it.isRegolaArchitetturale() }
            .assertTrue { file -> !file.text.contains("mockkStatic(") && !file.text.contains("mockkObject(") }
    }

    // --- CR-18 - Shared technical libraries carry no domain (ADR 0028) ----------------------------

    /**
     * The ubiquitous-language tokens of `.mismagent/context-map.md` (the bounded contexts' canonical nouns: Progetto,
     * Trascrizione, Parlanti, Sbobinatura, Sintesi). The list lives HERE only (CR-18b); a new context term amends it.
     */
    private val tokenUbiquitari = setOf(
        "Progetto", "Registrazione", "Elaborazione", "Trascritto", "Voce", "Segmento", "Revisione",
        "Parlante", "Impronta", "Attribuzione", "Sbobinatura", "Riassunto", "Fonte",
    )

    private fun nomeConTokenUbiquitario(nome: String): Boolean =
        tokenUbiquitari.any { nome.contains(it, ignoreCase = true) }

    /** ADR 0028 §2: `:supporto`'s public API, member-qualified. It must stay equal to the ADR (CR-18c). */
    private val apiPubblicaSupporto = setOf(
        "RitentaConBackoff",
        "RitentaConBackoff.avvia",
        "RitentaConBackoff.richiedi",
        "Segnalazione",
        "Segnalazione.segnala",
        "gestoreErroriNonCatturati",
        "figlioDi",
        "catturaNonFatale",
    )

    @Test
    fun `CR-18a i file di supporto non importano snastro fuori da snastro supporto`() {
        supporto
            .files
            .assertTrue { file ->
                val pkg = file.packagee?.name.orEmpty()
                (pkg == "snastro.supporto" || pkg.startsWith("snastro.supporto.")) &&
                    file.imports.none { it.name.startsWith("snastro.") && !it.name.startsWith("snastro.supporto.") }
            }
    }

    @Test
    fun `CR-18b nessuna dichiarazione di supporto contiene un termine del linguaggio ubiquitario`() {
        val nomi = supporto.declarations(includeNested = true, includeLocal = true)
            .mapNotNull { (it as? KoNameProvider)?.name }
        val violazioni = nomi.filter { nomeConTokenUbiquitario(it) }
        kotlin.test.assertTrue(violazioni.isEmpty(), "Termini del context-map in :supporto (CR-18b): $violazioni")
    }

    @Test
    fun `CR-18b il predicato riconosce i termini del context-map e lascia passare i nomi tecnici`() {
        kotlin.test.assertTrue(nomeConTokenUbiquitario("RitentaRegistrazione"))
        kotlin.test.assertTrue(nomeConTokenUbiquitario("improntaCorrente"))
        kotlin.test.assertTrue(apiPubblicaSupporto.none { nomeConTokenUbiquitario(it) })
    }

    @Test
    fun `CR-18c l API pubblica di supporto e esattamente quella di ADR 0028`() {
        kotlin.test.assertEquals(apiPubblicaSupporto, apiPubblica(supportoMain), "ADR 0028 §2 vs :supporto (CR-18c)")
    }

    /** Public top-level declarations plus the public members of public types, by declared names (no supertype walk). */
    private fun apiPubblica(scope: KoScope): Set<String> {
        val file = scope.files
        val tipi = file.flatMap { it.classes() }.filter { it.hasPublicOrDefaultModifier }
            .map { Triple(it.name, it.functions(includeNested = false), it.properties(includeNested = false)) } +
            file.flatMap { it.interfaces() }.filter { it.hasPublicOrDefaultModifier }
                .map { Triple(it.name, it.functions(includeNested = false), it.properties(includeNested = false)) } +
            file.flatMap { it.objects() }.filter { it.hasPublicOrDefaultModifier }
                .map { Triple(it.name, it.functions(includeNested = false), it.properties(includeNested = false)) }
        val membri = tipi.flatMap { (tipo, funzioni, proprieta) ->
            funzioni.filter { it.hasPublicOrDefaultModifier }.map { "$tipo.${it.name}" } +
                proprieta.filter { it.hasPublicOrDefaultModifier }.map { "$tipo.${it.name}" }
        }
        val radice = file.flatMap { f ->
            f.functions(includeNested = false).filter { it.hasPublicOrDefaultModifier }.map { it.name } +
                f.properties(includeNested = false).filter { it.hasPublicOrDefaultModifier }.map { it.name } +
                f.typeAliases.filter { it.hasPublicOrDefaultModifier }.map { it.name }
        }
        return (tipi.map { it.first } + membri + radice).toSet()
    }

    @Test
    fun `CR-18 nessun import di snastro supporto test in src main o src testFixtures`() {
        progetto
            .files
            .filter { file ->
                val percorso = file.path.replace('\\', '/')
                "/src/main/" in percorso || "/src/testFixtures/" in percorso
            }
            .assertTrue { file -> file.imports.none { it.name.startsWith("snastro.supporto.test") } }
    }

    // --- CR-19 - Shared primitives are used, not re-invented (ADR 0028 amendment 2026-09-30) ----------------

    /**
     * The code of a file without comments and string contents, so a forbidden call named in KDoc, a trailing
     * comment or a string never counts, and a construct spread over several lines (the house style of an annotated
     * `catch`) is seen whole. Raw strings, then block comments, then strings, then line comments.
     */
    private fun codice(testo: String): String = testo
        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "\"\"")
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex(""""(?:\\.|[^"\\\n])*""""), "\"\"")
        .replace(Regex("""//[^\n]*"""), "")

    private fun KoFileDeclaration.percorsoRelativo(): String =
        java.io.File(path).relativeTo(radice).invariantSeparatorsPath

    private fun KoFileDeclaration.occorrenze(regex: Regex): Int = regex.findAll(codice(text)).count()

    private val sleep = Regex("""\b(Thread|TimeUnit\.[A-Z_]+)\.sleep\(""")
    private val sleepImportato = Regex("""^import java\.lang\.Thread\.sleep\b""", RegexOption.MULTILINE)

    private fun KoFileDeclaration.dorme(): Boolean = occorrenze(sleep) > 0 || sleepImportato.containsMatchIn(text)

    /**
     * Frozen: the fixed pauses that are not in a test source set of a module that can reach `:supporto-test`.
     * `src/main` ones drive real time (audio watchdog, smoke run); the testFixtures one waits for M2/S5 (ADR 0028 §5).
     * A new entry needs a dated reason here.
     */
    private val sleepAmmessi = setOf(
        "avvio/src/main/kotlin/snastro/avvio/LettoreAudioReale.kt",
        "avvio/src/main/kotlin/snastro/avvio/smoke/Smoke.kt",
        "sintesi/applicazione/src/testFixtures/kotlin/" +
            "snastro/sintesi/applicazione/porte/ModelloLinguisticoContratto.kt",
    )

    private fun fuoriDaSupporto(p: String): Boolean =
        !p.startsWith("supporto/") && !p.startsWith("supporto-test/") && !p.startsWith("llama-jni/")

    @Test
    fun `CR-19a Thread sleep solo dentro supporto-test`() {
        val violazioni = progetto.files
            .filter { !it.isRegolaArchitetturale() }
            .filter { fuoriDaSupporto(it.percorsoRelativo()) && it.percorsoRelativo() !in sleepAmmessi }
            .filter { it.dorme() }
            .map { it.percorsoRelativo() }
        kotlin.test.assertTrue(violazioni.isEmpty(), "Thread.sleep fuori da :supporto-test (CR-19a): $violazioni")
    }

    private val catturaTutto = Regex(
        """\brunCatching\b|\bcatch\s*\((?:[^()]|\([^()]*\))*?:\s*(?:kotlin\.|java\.lang\.)?""" +
            """(?:Throwable|Exception|RuntimeException|Error)\s*,?\s*\)""",
    )

    private val nuovoScope = Regex(
        """(?<!\w)CoroutineScope\s*\(|\bMainScope\s*\(|\bGlobalScope\b|:\s*CoroutineScope\s*\{""",
    )

    /**
     * Frozen (CR-19b): the catch-alls of `src/main` outside `:supporto` on 2026-09-30, counted per file. Each was
     * reviewed with its detekt suppression (a presenter turning a failure into an error state, a queue or dispatcher
     * that condemns and rethrows, a best-effort cleanup). A count may only go DOWN: a new catch-all fails the gate,
     * and a removed one fails it too until the count here is lowered. New code uses `catturaNonFatale` or `Esito`.
     */
    private val catturaTuttoCongelati: Map<String, Int> = mapOf(
        "audio/src/main/kotlin/snastro/audio/RiproduttoreWav.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/CartellaLogApp.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/parlanti/AzioniSomiglianzaProgetto.kt" to 2,
        "avvio/src/main/kotlin/snastro/avvio/parlanti/ComandiVoceProgetto.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/parlanti/ModuloParlanti.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/progetto/ModuloProgetto.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/progetto/SessioneProgettoImpl.kt" to 8,
        "avvio/src/main/kotlin/snastro/avvio/trascrizione/SegnalatoreFaseConRilascio.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/trascrizione/SelezioneAdattatoriMl.kt" to 1,
        "kernel/src/main/kotlin/snastro/kernel/DispatcherEventiInMemoria.kt" to 4,
        "parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/comandi/RiallineaImpronteServizio.kt" to 2,
        "parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/comandi/" +
            "RiallineaTutteLeImpronteServizio.kt" to 1,
        "persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt" to 4,
        "trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/" +
            "EseguiProssimaElaborazioneServizio.kt" to 3,
        "ui/src/main/kotlin/snastro/ui/ShellPresenter.kt" to 2,
        "ui/src/main/kotlin/snastro/ui/impostazioni/ImpostazioniPresenter.kt" to 2,
        "ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenter.kt" to 2,
        "ui/src/main/kotlin/snastro/ui/lettore/LettorePresenter.kt" to 1,
        "ui/src/main/kotlin/snastro/ui/modelli/ModelliPresenter.kt" to 1,
        "ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt" to 3,
        "ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt" to 2,
        "ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt" to 4,
        "ui/src/main/kotlin/snastro/ui/registrazione/SomiglianzaVoci.kt" to 1,
        "ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt" to 5,
        "ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt" to 8,
        "ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt" to 2,
    )

    /** Frozen (CR-19c): the app root scope and S3's scope handed to [snastro.supporto.figlioDi]. Only goes down. */
    private val nuovoScopeCongelati: Map<String, Int> = mapOf(
        "avvio/src/main/kotlin/snastro/avvio/Grafo.kt" to 1,
        "avvio/src/main/kotlin/snastro/avvio/parlanti/ModuloParlanti.kt" to 1,
    )

    /** Files of `src/main` outside `:supporto` whose count differs from the frozen one, as `path=count`. */
    private fun scostamenti(regex: Regex, congelati: Map<String, Int>): List<String> {
        val contati = progetto.files
            .filter { "/src/main/" in it.percorsoRelativo() && fuoriDaSupporto(it.percorsoRelativo()) }
            .associate { it.percorsoRelativo() to it.occorrenze(regex) }
            .filterValues { it > 0 }
        return (contati.keys + congelati.keys).sorted()
            .filter { contati[it] ?: 0 != congelati[it] ?: 0 }
            .map { "$it=${contati[it] ?: 0} (congelato ${congelati[it] ?: 0})" }
    }

    @Test
    fun `CR-19b catch-all e runCatching in src main solo in supporto o nei conteggi congelati`() {
        val scostamenti = scostamenti(catturaTutto, catturaTuttoCongelati)
        kotlin.test.assertTrue(
            scostamenti.isEmpty(),
            "Usa catturaNonFatale o Esito; se ne hai tolto uno abbassa il conteggio (CR-19b):\n" +
                scostamenti.joinToString("\n"),
        )
    }

    @Test
    fun `CR-19c scope costruiti a mano in src main solo in supporto o nei conteggi congelati`() {
        val scostamenti = scostamenti(nuovoScope, nuovoScopeCongelati)
        kotlin.test.assertTrue(
            scostamenti.isEmpty(),
            "Usa figlioDi; se ne hai tolto uno abbassa il conteggio (CR-19c):\n" + scostamenti.joinToString("\n"),
        )
    }

    @Test
    fun `CR-19 i predicati vedono le forme reali e ignorano commenti e stringhe`() {
        fun vede(regex: Regex, testo: String) = regex.findAll(codice(testo)).count()
        kotlin.test.assertEquals(1, vede(sleep, "    Thread.sleep(10)"))
        kotlin.test.assertEquals(1, vede(sleep, "x; TimeUnit.SECONDS.sleep(1)"))
        val commenti = "/**\n * no `Thread.sleep(1)`\n */\n// Thread.sleep(2)\nf() // Thread.sleep(3)"
        kotlin.test.assertEquals(0, vede(sleep, commenti))
        kotlin.test.assertEquals(0, vede(sleep, "val s = \"Thread.sleep(4)\"\n/* Thread.sleep(5)\n still */"))
        kotlin.test.assertTrue(sleepImportato.containsMatchIn("package a\nimport java.lang.Thread.sleep\n"))
        kotlin.test.assertEquals(1, vede(catturaTutto, "val r = runCatching { x() }"))
        kotlin.test.assertEquals(1, vede(catturaTutto, "} catch (e: Exception) {"))
        kotlin.test.assertEquals(1, vede(catturaTutto, "} catch(e : java.lang.Throwable) {"))
        kotlin.test.assertEquals(1, vede(catturaTutto, "} catch (_: Error) {"))
        val annotato = "} catch (\n    // why\n    @Suppress(\"TooGenericExceptionCaught\", \"X\") e: Exception,\n) {"
        kotlin.test.assertEquals(1, vede(catturaTutto, annotato))
        val specifici = "} catch (e: IOException) {\n} catch (e: CancellationException) {"
        kotlin.test.assertEquals(0, vede(catturaTutto, specifici))
        kotlin.test.assertEquals(1, vede(nuovoScope, "val s = CoroutineScope(SupervisorJob())"))
        kotlin.test.assertEquals(1, vede(nuovoScope, "val s = kotlinx.coroutines.CoroutineScope (io)"))
        kotlin.test.assertEquals(1, vede(nuovoScope, "val s = MainScope()"))
        kotlin.test.assertEquals(1, vede(nuovoScope, "GlobalScope.launch { }"))
        kotlin.test.assertEquals(1, vede(nuovoScope, "object : CoroutineScope {"))
        kotlin.test.assertEquals(0, vede(nuovoScope, "val s = rememberCoroutineScope()\nfun f(s: CoroutineScope) = s"))
    }

    private companion object {
        /** La radice del progetto: `:architettura-test` gira con la propria cartella come `user.dir`. */
        val radice: java.io.File = java.io.File(System.getProperty("user.dir")).parentFile

        /**
         * Cartelle in radice che non sono sorgenti di QUESTO albero: `.worktrees` (i worktree git di altri
         * rami, copie intere del progetto) e, come fa gia `scopeFromProject`, `.gradle` e `build`.
         */
        val cartelleEscluse = setOf(".worktrees", ".gradle", "build")

        /** Segmenti di percorso esclusi a ogni profondita, come in `scopeFromProject` (output di build). */
        val segmentiEsclusi = setOf("build", "target", "buildSrc")

        /**
         * Il progetto analizzato UNA volta per l'intera classe (JUnit crea un'istanza per test): ogni
         * regola filtra lo stesso scope. Stessi file di `Konsist.scopeFromProject()`, tranne `.worktrees`:
         * lo scope parte dalle cartelle di radice, cosi i worktree annidati non vengono nemmeno letti
         * (`scopeFromProject` li analizzerebbe tutti prima di qualunque filtro).
         */
        /** Only `./supporto` and `./supporto-test` (CR-18a/b), build output excluded: never reads outside them. */
        val supporto: KoScope by lazy { scopeDi("supporto", "supporto-test") }

        /** `:supporto`'s main sources only: its public API (CR-18c). */
        val supportoMain: KoScope by lazy { scopeDi("supporto/src/main") }

        private fun scopeDi(vararg cartelle: String): KoScope =
            Konsist.scopeFromExternalDirectories(cartelle.map { java.io.File(radice, it).absolutePath })
                .slice { file ->
                    java.io.File(file.path).relativeTo(radice).invariantSeparatorsPath
                        .split('/')
                        .none { it in segmentiEsclusi }
                }

        val progetto: KoScope by lazy {
            val cartelle = radice.listFiles { f -> f.isDirectory && f.name !in cartelleEscluse }.orEmpty()
            Konsist.scopeFromExternalDirectories(cartelle.map { it.absolutePath })
                .slice { file ->
                    java.io.File(file.path).relativeTo(radice).invariantSeparatorsPath
                        .split('/')
                        .none { it in segmentiEsclusi }
                }
        }
    }
}
