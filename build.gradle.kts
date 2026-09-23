import org.gradle.api.artifacts.ProjectDependency

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

// --- Opt-in tasks (NEVER wired into `check`) --------------------------------------------------
// Stubs at wave 0: the real behaviour arrives with the blocks named below. Kept here (root) so
// `./gradlew tasks` and CI documentation see one stable entry point.

tasks.register("modelliTest") {
    group = "verification"
    description = "Opt-in: real ML/audio adapter contract tests (@Tag(\"modelli\")). Stub until the ml-sherpa-motore block."
    doLast {
        logger.lifecycle(
            "modelliTest: no @Tag(\"modelli\") adapters yet (wave-0 scaffold stub). " +
                "Once real adapters exist this task will run: ./gradlew test --tests ... -DincludeTags=modelli",
        )
    }
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

tasks.register("scaricaNativiSherpa") {
    group = "build setup"
    description = "Fetches sherpa-onnx + onnxruntime native libs (pinned release + SHA-256) into a build cache. Stub until ml-sherpa-motore."
    doLast {
        logger.lifecycle(
            "scaricaNativiSherpa: stub (wave-0 scaffold) — coordinates/SHA-256 not yet in the catalogue. " +
                "Filled in by the ml-sherpa-motore block; never downloads in this wave, never part of check.",
        )
    }
}
