# snastro — Code-writing rules

> Written by the architect (model movement, 2026-09-23) from the rules the user deliberated in
> ARCH_PROPOSAL (knobs K1–K6 accepted as recommended; K3 re-confirmed as `Esito`). Change a rule
> only through a new deliberation — each change cites its ADR. Every rule carries its
> **enforcement channel**; a rule with no channel is not written here.
> Style and module map: `architecture.md`. Stack: Kotlin/JVM, Compose Desktop (ADR 0001).
> Codebase conventions (style memory): `architetture/dev-architecture-app.md`.
> **Deltas:** 2026-09-23 (targeted style dispatch) — CR-14…CR-17, RC-9 · 2026-09-23 (R25 amendment) — CR-8, RC-4 ·
> 2026-09-23 (fix-batch-10) — CR-1.

Channels:
- **gate lint** — runs inside `./gradlew check` (the worker's own loop, verifier step 2, CI). Tools and
  config paths (wired by the wave-0 scaffold):
  - Gradle module graph + root task **`verificaDipendenzeModuli`** — `build.gradle.kts` (root),
    edges table = `architecture.md` § Allowed dependency edges;
  - **Konsist** — `architettura-test/src/test/kotlin/snastro/architettura/`;
  - **detekt** (+ `detekt-formatting` / ktlint rules), **no baseline** — `config/detekt/detekt.yml`;
  - **Kotlin compiler** — `allWarningsAsErrors = true` (all modules), `explicitApi()` on `:kernel` and
    every `*:applicazione` — convention plugin in `build-logic/`.
- **review criterion** — checked by `code-review` (cite the rule id in the finding).
- **structural** — owned by a method skill/gate; cited, not restated.

## Mechanical rules (gate lint)

**CR-1 · Dependency rule.** `adattatori → applicazione → dominio → kernel`; cross-context only
`consumer:adattatori → supplier:applicazione`; `ui` sees only `*:applicazione` + `kernel`, plus (a)
itself (`snastro.ui` / `snastro.ui.*` — intra-`:ui` imports across screens and shared UI code, e.g.
`snastro.ui.testi.*`) and (b) each context's `dominio`-owned error hierarchy ONLY —
`snastro.<ctx>.dominio.Errore<Nome>`, including nested members (e.g.
`ErroreProgetto.NomeProgettoVuoto`) — never an aggregate/VO of `dominio`. (b) exists because
`MessaggiErrore` (`:ui`) maps `ErroreProgetto`/`ErroreTrascrizione`/`ErroreParlanti` exhaustively
(ADR 0003 (b), RC-4); no new Gradle edge is needed — `*:applicazione` already exposes its own
`*:dominio` as `api`, so it is reachable for compilation transitively, and `:ui` never declares a
direct `*:dominio` dependency.
→ gate lint: Gradle module graph + `verificaDipendenzeModuli`; Konsist (package-level: no
`snastro.<a>.*` import of `snastro.<b>.dominio|adattatori` for `a ≠ b`; a narrower `:ui`-only
Konsist rule allows exactly (a) and (b) above, nothing else of `dominio`). Backup: ADR 0002
`enforced_by`.
*(amended 2026-09-23, fix-batch-10 — ui error hierarchies + intra-ui: the original `:ui` rule was
"kernel or `.applicazione`" only, which wrongly rejected both intra-`:ui` imports and the ADR 0003
(b) `dominio` error hierarchies `MessaggiErrore` must map.)*

**CR-2 · Inner modules are pure.** `:kernel`, `*:dominio`, `*:applicazione` import no framework,
I/O, persistence, UI, ML, audio or network API — incl. JDK `java.sql`, `java.net`, `java.nio.file`,
`java.io.File`, `javax.sound`. → gate lint: Konsist (+ ADR 0002 `enforced_by`).

**CR-3 · Technical confinement.** `com.k2fsa` / `System.load*` only in `:ml-sherpa` (ADR 0004);
`org.bytedeco` / `javax.sound` only in `:audio`, never an FFmpeg `-gpl` artifact (ADR 0005);
JDBC / SQLDelight / `org.sqlite` only in `:persistenza` and `:progetto|:trascrizione|:parlanti:adattatori`
(ADR 0006); network APIs only in `:modelli` (ADR 0008); `.md` read APIs never in `documento`
(ADR 0010). → gate lint: Konsist (+ each ADR's `enforced_by`).

**CR-4 · Aggregates are encapsulated, never `data class`.** Aggregate roots are plain classes: state
in `private set` properties / private mutable collections exposed as read-only views, changed only
through methods; no public `var` anywhere in `*:dominio`. → gate lint: Konsist (classes in
`..dominio..` named as the tactical model's roots are not `data`; no public mutable property in
`..dominio..`).

**CR-5 · Values are immutable.** VOs, domain events, published events, port DTOs and read-model
views are `data class` with `val` only, or `@JvmInline value class` for single-field VOs and ids.
**No `data class` with an `Array`/`FloatArray`/`ByteArray` property** (reference equality) — wrap it
in a class with explicit `equals`/`hashCode` (e.g. `Impronta`, `CampioniAudio`). → gate lint: Konsist.

**CR-6 · No `!!`.** → gate lint: detekt `UnsafeCallOnNullableType` (main source sets).

**CR-7 · No swallowed failure.** No empty `catch`, no catch-all that drops the error, no
`runCatching` whose failure is ignored. → gate lint: detekt `EmptyCatchBlock`, `SwallowedException`,
`TooGenericExceptionCaught`, `TooGenericExceptionThrown`.

**CR-8 · Expected failures are values.** `ErroreDominio` is a plain (non-sealed) interface in
`:kernel`, never a `Throwable`; each context owns one sealed hierarchy `Errore<Contesto> : ErroreDominio`
in `:<ctx>:dominio`'s `Errori<Contesto>.kt` for rule violations AND lookup misses (`…NonTrovato`,
`…GiaPresente`), plus at most one `ErroreApplicazione<Contesto>` per `applicazione` module (package
`…applicazione.porte`) only for technical/adapter failures; aggregate methods / application services return `Esito` for expected rule
violations (ADR 0003). → gate lint: Konsist (no subtype of `ErroreDominio` extends `Throwable`;
every direct subtype of `ErroreDominio` is a `sealed interface` named `Errore<X>`; public functions
of `*:applicazione` command handlers return `Esito`) + ADR 0003 `enforced_by`.
*(amended 2026-09-23, R25 — was "`ErroreDominio` is a sealed interface")*
*(amended 2026-09-23 (b), ADR 0003 placement clarification)*

**CR-9 · Warnings are errors; public API is explicit.** → gate lint: compiler
(`allWarningsAsErrors`, `explicitApi()` on `:kernel` + `*:applicazione`).

**CR-10 · Ubiquitous-language names (K6).** Domain/application/UI declarations use the Italian
canonical terms of `context-map.md` exactly, **ASCII only** (`UltimaAttivita`, not `UltimaAttività`);
technical scaffolding may be English. The context-map's "Not:" synonyms (e.g. `Speaker`, `Cluster`,
`Transcript`, `Job`, `Utterance`, `Chunk`, `Embedding`, `Voiceprint`, `Score`, `Confidenza`,
`Merge`, `Mapping`, `Workspace`, `Meeting`) must not name a declaration in `*:dominio`,
`*:applicazione`, `:ui`. → gate lint: Konsist (declaration names vs the synonym list, kept in
`architettura-test` next to the rule; non-ASCII identifiers rejected).

**CR-11 · Formatting.** → gate lint: detekt-formatting (ktlint rules), official Kotlin style.

**CR-12 · Build files reference projects with `project(":…")` only** (no type-safe accessors), so the
edge check sees every edge. → gate lint: `verificaDipendenzeModuli`.

**CR-13 · Migrations are forward-only and are the schema (ADR 0006 (a)).** New schema = a new `.sqm`
(`deriveSchemaFromMigrations`; `.sq` = queries only); a shipped `.sqm` is never edited. The baseline
`SnastroDatabase.Schema.version` is **2** (`1.sqm` is SQLDelight's 1→2 migration step, not "version
1" itself — a persisted `user_version = 1` was never really shipped and is refused, not migrated).
→ gate: the `:persistenza:test` migration test (empty DB → current version, integrity + every query,
`Schema.migrate` from empty matches `Schema.create`) and the query compilation against the derived
schema; the "never edited" half is a **review criterion** (diff touches a shipped `.sqm` → finding).

**CR-14 · No wall clock in the inner layers.** `*:dominio` and `*:applicazione` never call
`Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `ZonedDateTime.now()`,
`Clock.systemDefaultZone()`, `Clock.systemUTC()`, `System.currentTimeMillis()`, `System.nanoTime()`;
services receive an injected `java.time.Clock`. → gate lint: Konsist.
*(delta 2026-09-23, dev-architecture `#pacchetti`)*

**CR-15 · Reconstitution only from persistence adapters.** `@OptIn(RicostituzioneDaPersistenza::class)`
(and any call of an aggregate's `ricostituisci`) appears only in `..adattatori.persistenza..`; the
annotation is `@RequiresOptIn(level = ERROR)`, so the compiler blocks unopted callers.
→ gate lint: compiler (opt-in) + Konsist (opt-in location). *(delta 2026-09-23, `#aggregato`)*

**CR-16 · Command services expose only `esegui`.** Every class in `..applicazione.comandi..` named
`*Servizio` has exactly one public function, `esegui`, returning `Esito`, and it is not `suspend`.
→ gate lint: Konsist. *(delta 2026-09-23, `#servizio`)*

**CR-17 · MockK scope.** No `io.mockk` import in any `src/testFixtures` source set, nor in any
`*Contratto` class or its subclasses; no `mockkStatic`/`mockkObject` anywhere. → gate lint: Konsist
(test source sets). The judgment half — MockK only for interaction checks where a recording fake
would be the only alternative, never stubbing a port that has a fake — is a **review criterion**
(RC-9). Build-level: `mockk` is declared `testImplementation` only. *(delta 2026-09-23, `#test`)*

## Discursive rules (review criteria)

**RC-1 · The root owns the rule.** A command goes through the aggregate that owns the invariant;
no application service, adapter or presenter re-implements an `[INV-n]` check (the service may
pre-check only set rules INV-4/INV-16, backed by ADR 0007's indexes).

**RC-2 · Thin UI.** Presenters (state holders) hold UI state and call `applicazione` only; they
contain no domain rule. Composables render presenter state and forward events — no logic, no I/O.

**RC-3 · Ports speak Published Language.** Port and published-event signatures use kernel VOs /
primitives / the wrappers of CR-5 — never sherpa, FFmpeg, SQLDelight, Compose or `java.nio` types.

**RC-4 · Exhaustive error mapping.** A `when` over a context hierarchy `Errore<Contesto>` has no
`else` branch (a new error type must break compilation where it is not handled). The single
dispatch over `ErroreDominio` (non-sealed) may have exactly one `else`, which is a programmer error
(`error(…)`), never a generic user message. *(amended 2026-09-23, R25)*

**RC-5 · Native resources are released.** Every sherpa/FFmpeg native object is used via `use {}` or
an `AutoCloseable` wrapper and never escapes its adapter (ADR 0004/0005).

**RC-6 · Biometric hygiene.** No copy of an `ImprontaVocale` outside `impronta_vocale` rows: no file,
cache, log of embedding values; every removal path deletes rows in the command's transaction
(ADR 0009).

**RC-7 · Transaction placement.** Invariant-carrying policies run inside the command's transaction;
`Documento` `Rigenerazione` runs after commit, idempotent; the ML pipeline never holds a transaction
(ADR 0012).

**RC-8 · Offline inference.** No inference/adapter path triggers a download or a network call
(ADR 0008) — beyond CR-3's mechanical part, review that `:modelli` download is invoked only from the
onboarding/startup flow.

**RC-9 · Fakes first.** Every port has a hand-written `<Porta>Finta` passing its `Contratto`; a
test stubbing such a port with MockK is a finding. MockK is acceptable only to verify an interaction
(call count/order/absence) that a recording fake would otherwise be written just for (CR-17,
dev-architecture `#test`).

## Structural (owned by the method — cited, not restated)
- **SRP / granularity** — one block = one reason to change: the tactical-model → block map
  (`build-manifest`) + `realize-*` skills.
- **LSP** — every adapter passes its port's contract test unchanged: `seam-in-process` +
  `realize-port` / `realize-adapter` (D2).
- **ISP** — consumer-owned ports with only the methods the consumer needs: `realize-port`.
- **Invariant tests** — one test per `[INV-n]`, named with the `INV-n ` tag (JVM-safe: no
  `[ ] . ; : / < >` in backtick names): `realize-aggregate`, `realize-application-service`.
- **UI split + render check** — presenter/view split and the render check: `realize-ui`,
  `run-app-smoke`.
- **KISS / YAGNI** — the worker's frugality ladder.
- **Naming anti-shadow** — the verifier's canonical-name check (complements CR-10).
