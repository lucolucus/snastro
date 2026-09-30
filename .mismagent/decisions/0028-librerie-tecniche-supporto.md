---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: architecture.md (module map, edges table, direction summary), code-rules.md (CR-18 new, CR-2 note), dev-architecture-app.md (#test, #dipendenze-test), ADR 0012 (retry wording of the after-commit workers), ADR 0023 (the queue reports escaped throwables)
closes_spike: null
decided: 2026-09-27 · user (post-R3 design review, analysis §2.4 X5/X8/X9/X11, §2.7 T1/T2, §6.5; every option as recommended: A1–A5) · architect (contents of the first cut, check names)
enforced_by:
  - check: architettura-test/controlli-adr/adr-0028-supporto-senza-progetti.sh
    from: a0-supporto-moduli
    # FAIL if supporto/build.gradle.kts or supporto-test/build.gradle.kts is missing, or contains `project(`, `rootProject`,
    # `rootDir` or `../` (comment lines stripped). The two modules reach no snastro module; the convention plugins stay allowed.
  - check: architettura-test/controlli-adr/adr-0028-supporto-test-solo-nei-test.sh
    from: a0-supporto-moduli
    # FAIL if supporto-test/build.gradle.kts is missing, or (1) any module build.gradle.kts (below the root) names
    # `:supporto-test` on a line whose configuration is not testImplementation / testRuntimeOnly (testFixtures* included in
    # the FAIL set until block m2-testfixtures-fuori-da-avvio is integrated — then this clause is relaxed by a dated
    # amendment here), or (2) any `import snastro.supporto.test` appears under */src/main or */src/testFixtures (comment
    # lines stripped).
  # Delivered 2026-09-27 by block a0-supporto-moduli: both scripts + red-green fixtures under fixture/<check>/, validated
  # by ControlliAdrTest. Konsist half (code-rules.md CR-18a/b/c) is the in-loop channel; these scripts are the verifier's backup.
---
# 0028 — Shared technical libraries without domain: `:supporto` and `:supporto-test`

## Context
The post-R3 design review (`analisi-design.md` §2.4, §2.7) found the same technical patterns re-invented block by
block:
- the "conflated channel + backoff" worker exists twice. `AbbonatoDocumentoEventi` and `AbbonatoRiallineamentoImpronte`
  have the same code. Neither logs, and both `runCatching` around `CancellationException` and `Error` (pre-release
  L276/L277);
- seven scopes are built by hand as `CoroutineScope(parent + SupervisorJob(...))`, and only R2 has an uncaught-error
  handler;
- `CodaCondivisa.eseguiProtetto` swallows every `Throwable`, OOM included, with no log (X8);
- about 14 private copies of `attendi`/`attendiFinche`, about 40 `Thread.sleep` in tests (T1), and no guaranteed scope
  cancellation (T2). These are the known flakes AC-417/459/479.

Analysis §6.5 asked where the worker lives. `:kernel` has no library dependency today and is on every `dominio`
classpath, so coroutines there would reach the domain. `:persistenza` is unreachable from `:documento:adattatori` and
has the wrong cohesion.

The user approved a separate shared library with strict guardrails. Consumer-owned ports stay duplicated on purpose
(ADR 0002, ADR 0021, analysis §4).

## Decision

### 1. Two new modules
| Gradle module | Package | Libraries | Project dependencies |
|---|---|---|---|
| `:supporto` | `snastro.supporto` | `kotlinx-coroutines-core` only | **none**, not even `:kernel` [user A3] |
| `:supporto-test` | `snastro.supporto.test` | `kotlin-test`, `junit-jupiter`, `kotlinx-coroutines-test` | **none** |

Directories `./supporto/` and `./supporto-test/`. Both apply `snastro.kotlin-jvm` + `snastro.explicit-api` (the
`-test` suffix follows `:architettura-test`). Neither is a `java-test-fixtures` producer.

### 2. `:supporto`: first cut (the public API, pinned by CR-18c)
- **`RitentaConBackoff<K>`.** A background worker:
  - coalescing keys over a conflated signal;
  - a bounded exponential backoff with a configured cap;
  - an injected `Segnalazione` hook, called on every failure with the key and the cause, and on recovery;
  - `avvia(scope)` / `richiedi(chiave)`.
  - It **rethrows** `CancellationException` and every `Error`. It never uses `runCatching` around them.
  - A failed key is retried until it succeeds or the scope is cancelled, and every failure is reported, so a
    poisoned unit is never retried silently.
  - The job is `suspend (K) -> Boolean`: true means done, false means retry. The caller maps its own `Esito`
    to that Boolean, because `:supporto` knows no `Esito`.
- **`gestoreErroriNonCatturati(segnala: Segnalazione): CoroutineExceptionHandler`.**
- **`figlioDi(genitore: CoroutineScope, dispatcher: CoroutineDispatcher? = null, gestore: CoroutineExceptionHandler): CoroutineScope`.**
  It returns a child scope with a `SupervisorJob` tied to the parent's `Job`.
- **`catturaNonFatale(blocco): Result<T>`.** It rethrows `CancellationException` and `VirtualMachineError`, and
  catches everything else. It is the one sanctioned catch-all at the edges (CR-7), so the detekt suppressions
  shrink to meaningful ones.
- **`fun interface Segnalazione { fun segnala(messaggio: String, causa: Throwable?) }`.** It is the only logging
  channel. `:supporto` never touches JUL. `:avvio` configures logging (the rotating `FileHandler`, X9) and injects
  the implementation.

### 3. `:supporto-test`: first cut
- `attendiFinche(timeout: Duration = 5.seconds, messaggio: String, condizione: () -> Boolean)`. It polls with a
  short sleep and fails with `messaggio`.
- `OrologioFinto(inizio: Instant, zona: ZoneId = UTC)`. It is a `java.time.Clock` with `avanza(Duration)` and
  `imposta(Instant)`. It replaces the sleeps used to separate instants (e.g. `ComposizioneR3Test` `richiestoAlle`).
- `conScopeDiProva { scope -> }`. It runs the block and cancels the scope in `finally`, with `StandardTestDispatcher`
  helpers for `runTest` + `backgroundScope`.
- `EsitoAtteso` (`atteso`/`erroreAtteso`) **stays** in `:kernel` testFixtures, because it uses kernel types.

### 4. Explicitly excluded (they stay where they are, or go where their own finding says)
- anything carrying a context type, an id, `Esito`, `ErroreDominio`, or a business rule;
- any claim/run/complete template (analysis C7: the two contexts' invariants differ);
- consumer-owned ports, every `…Contratto` and `…Finta` (they stay in the supplier's testFixtures);
- `NomeFileSicuro` and `VoceId.etichetta` (kernel vocabulary);
- `LayoutCartellaProgetto` (`:audio`, ADR 0010);
- `violazioneUnica` (`:persistenza`);
- `unisci(AggiornamentiVista)` and `ArrestoProgetto` (`:avvio`, because they use `:ui` types, [ADR 0030](0030-composizione-unica-per-contesto.md));
- `ricaricaUltima` and `unaAllaVolta` (`:ui`-only presenter helpers);
- JUL/`FileHandler` configuration (`:avvio`).

A new public declaration in `:supporto` is an amendment of this ADR (CR-18c).

### 5. Allowed edges [user A2, A4]
| From | May depend on `:supporto` | May depend on `:supporto-test` |
|---|---|---|
| every `*:adattatori`, `:avvio`, `:ui` | yes (`implementation`) | test configurations only |
| `:kernel`, every `*:dominio`, every `*:applicazione` | **no** (CR-2: inner modules stay pure) | test configurations only |
| `:persistenza`, `:audio`, `:ml-sherpa`, `:modelli`, `:llama-jni` | no (a real use is an amendment) | test configurations only (not `:llama-jni`, ADR 0027) |
| `:architettura-test` | yes (every module) | yes |

- `verificaDipendenzeModuli` gains the rows `":supporto" to emptySet()` and `":supporto-test" to emptySet()`, and
  `:supporto` is added to the sets of `*:adattatori`, `:ui` (and `:avvio` has every module already).
- It also gains ONE test-only rule: an edge to `:supporto-test` passes from any module (except `:llama-jni`) if and
  only if the configuration is `testImplementation` or `testRuntimeOnly`.
- **`testFixtures*` stays forbidden until M2/S5.** M2/S5 is the block that removes `implementation(testFixtures(...))`
  from `:avvio`. Until then, a testFixtures edge would ship `:supporto-test` inside the app [user A4]. The
  relaxation is a dated amendment here.
- Test fixtures that duplicate `attendiFinche` stay where they are until then, and are migrated afterwards.

### 6. Guardrails
- **Konsist, in-loop (`code-rules.md`):**
  - CR-18a: no import of `snastro.*` outside the module's own package;
  - CR-18b: no declaration name containing a ubiquitous-language token of `context-map.md`;
  - CR-18c: `:supporto`'s public API equals the list in §2;
  - also: no `snastro.supporto.test` import in `src/main` or `src/testFixtures`.
- **sh (`enforced_by`, the verifier's backup):** the two checks in the frontmatter.
- **Module graph:** the empty rows make any snastro dependency a build failure.

### 7. Migration order (one PR per block, gate green each time)
1. `a0-supporto-moduli`: both modules, `settings.gradle.kts`, the allowlist rows and the test-only rule, the Konsist
   rules, the two scripts with red-green fixtures. The empty skeleton passes `./gradlew check`.
2. `a1-supporto-test-adozione`: `attendiFinche`, `OrologioFinto` and `conScopeDiProva` replace the private copies,
   test source sets only. First `:avvio` (most copies, the flakes), then `:ui`, then `:audio`, `:progetto`, `:sintesi`.
   A default JUnit timeout (analysis T2) lands here too.
3. `a2-ritenta-documento`: `AbbonatoDocumentoEventi` runs on `RitentaConBackoff`. This closes L276/L277.
4. `a3-ritenta-parlanti`: `AbbonatoRiallineamentoImpronte` runs on `RitentaConBackoff`, and `PorteImprontaConLog` is
   retired, because the hook logs.
5. `a4-supporto-avvio`:
   - one `gestoreErroriNonCatturati` wired to the JUL `Segnalazione`;
   - `figlioDi` at the existing hand-built scopes;
   - `CodaCondivisa` gains a `segnalaSfuggito` hook and rethrows `VirtualMachineError` (ADR 0023 note).
   - The scopes move again in [ADR 0030](0030-composizione-unica-per-contesto.md)'s `c3`.

## Rejected options
- **Coroutines in `:kernel`.** They would put a library on every `dominio` classpath and weaken CR-2 for one
  worker.
- **The worker in `:persistenza`.** `:documento:adattatori` has no edge to it, and a retry loop is not persistence.
- **One broad `:comune` module.** The name invites a dumping ground. `:supporto`'s pinned API is the counterweight.
- **`:supporto` depending on `:kernel`** (an `Esito`-aware retry). It would open the door to context ids. The
  Boolean job keeps the module free of vocabulary.

## Consequences
- Two new rows in `architecture.md`'s module map and edges table, and a new direction-summary line.
- `code-rules.md`: CR-18 is new; CR-2 gains the note "`:kernel` has no coroutine dependency".
- `dev-architecture-app.md` `#test` makes `attendiFinche` and `OrologioFinto` mandatory: no private copies, no
  `Thread.sleep` to separate instants. `#dipendenze-test` names the test-only edge.
- The retry wording in ADR 0012 and the swallow in ADR 0023 are re-pointed here by dated notes.
- Not decided here: who may extend `ErroreDominio` (analysis §6.3). `:supporto` declares no `ErroreDominio`
  subtype, and cannot, having no `:kernel` edge.

## Amendment 2026-09-30 — the primitives are enforced where they are used (CR-19) [user]
The migration in §7 replaced the copies that existed on 2026-09-27, but nothing stopped the next block from writing a
new one: by 2026-09-30 `main` again had about 35 `Thread.sleep` in tests and two private `attendiFinche` in
`:ml-sherpa`. The user asked that the harness's code errors not recur (feature `incontro`, explore). So:
- `:supporto-test` gains `restaVeroPer(durata, messaggio) { condizione }` — the negative twin of `attendiFinche`: the
  condition is checked throughout the window, failing as soon as it turns false — and `pausaInTempoReale(durata,
  motivo)`, the one sanctioned fixed pause, reason mandatory. §3's first cut becomes five helpers.
- `code-rules.md` CR-19a/b/c make the use of `:supporto` / `:supporto-test` a gate rule (Konsist): fixed pauses only
  in `:supporto-test`; catch-alls and `runCatching` in `src/main` only in `:supporto`; hand-built scopes only in
  `:supporto`. What existed and is correct is kept in frozen lists inside the rule, which may only shrink.
- Every test sleep of `main` was migrated in the same change; the four copies of the Cambiamenti collector in
  `:avvio` tests became one, `AmbienteProgetto.raccogliCambiamenti`.

