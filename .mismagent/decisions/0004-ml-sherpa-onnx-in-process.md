---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by:   # migrated 2026-09-26 (mismAgent 0.22) from the legacy inline shell rule: same grep/find logic, now versioned checks run by the gate (architettura-test ControlliAdrTest, red-green on fixture/<check>/)
  - check: architettura-test/controlli-adr/adr-0004-sherpa-confinato.sh
amended: 2026-09-26   # "Amendment 2026-09-26 (b) (ADR 0027)" (the admitted path becomes the library ./llama-jni/; ./llm/ withdrawn; fixtures reworked); earlier: "Amendment 2026-09-26 (ADR 0026)" (System.load also admitted in the top-level ./llm/ module; check + fixtures amended); earlier: "Amendment 2026-09-25 (ADR 0023)" (the serial queue is SHARED with Riassunti); earlier: see "Amendment 2026-09-23" (enforced_by scope: --exclude-dir=architettura-test), "Amendment 2026-09-23 (b)" (no automatic start, ADR 0014 [user]) and "Amendment 2026-09-24" (native coordinates, fetch, explicit load, provider — ADR 0016)
---
# 0004 — ML runtime: sherpa-onnx (JNI) in-process, confined to `:ml-sherpa`, serial pipeline

## Context
Diarization, ASR, VAD and speaker embeddings must run locally, offline, cross-platform. The user
chose all-Kotlin with **sherpa-onnx** (k2-fsa) Java/JNI bindings (ADR 0001). Open question from
pass-1: in-process vs a child JVM for crash isolation of native code. Which *models* run is still
open (spikes `scelta-diarizzatore`, `scelta-asr-code-switching`, `impronta-vocale-affidabilita`,
`allineamento-parole-voci`); `packaging-modelli-desktop` proves the hello-world.

## Decision
- **Library:** sherpa-onnx Java API (`com.k2fsa.sherpa.onnx.*`: `OfflineSpeakerDiarization`,
  `OfflineRecognizer`, `SpeakerEmbeddingExtractor`, `Vad`) + per-OS native libs
  (`libsherpa-onnx-jni` + onnxruntime; macOS arm64/x64, Windows x64, Linux x64) from a **pinned
  sherpa-onnx release, fetched by a Gradle task with SHA-256 verification into a build cache —
  never committed** (profile boundary rule; `.gitignore`). Whether Maven coordinates exist is
  verified by the packaging spike; the pinning discipline holds either way.
- **Confinement:** `com.k2fsa` imports and `System.load`/`System.loadLibrary` live **only** in
  `:ml-sherpa` (enforced_by). sherpa types never appear in a port signature: audio samples cross as a
  `CampioniAudio` wrapper (`FloatArray`, 16 kHz mono), embeddings as an `Impronta` wrapper with
  explicit `equals`/`hashCode`.
- **Ports** (consumer-owned, declared in the consumer's `applicazione`): `Trascrizione` →
  `DecodificatoreAudio`, `Diarizzatore`, `RiconoscitoreParlato`, `Vad`, `Allineatore`,
  `SegnalatoreFase`; `Parlanti` → `EstrattoreImpronta`, `ConfrontoImpronte`. `Allineatore` and
  `ConfrontoImpronte` (cosine similarity + `SoglieFascia`) are **pure Kotlin**, testable in the gate.
- **Execution: IN-PROCESS [user]** on a **dedicated single-thread pipeline dispatcher**; one
  `Elaborazione` at a time from a **serial FIFO queue** (policy `RegistrazioneAggiunta` →
  `AvviaElaborazione` queued `in_attesa` — *the automatic policy is SUPERSEDED by "Amendment
  2026-09-23 (b)" below: the user starts each `Elaborazione`; the queue is unchanged*). ONNX intra-op threads = number of performance cores.
  Every native handle is wrapped in an `AutoCloseable` and released (`use {}`) at the end of the
  `Elaborazione` (~1–2 GB off-heap).
- **Progress:** the pipeline reports its `FaseElaborazione`
  (`decodifica | diarizzazione | trascrizione | allineamento`) through `SegnalatoreFase` — progress
  information only, not guarded state; no percentage. (The term is pending a context-map amendment
  by the analyst — see `architecture.md`.)
- **Crash recovery:** a native crash kills the app; committed data is safe and the startup policy
  (`in_corso` with no live run → `fallita`, retry offered) recovers. **Escape hatch (documented, not
  built):** a child-JVM adapter behind the same ports if spikes or real use show native crashes —
  it would need a superseding ADR.
- **Execution provider:** **CPU by default everywhere.** CoreML (macOS) / CUDA / DirectML only as an
  opt-in setting, and only if a spike measures a real gain (ADR 0011).
- **Fallback if sherpa diarization quality is insufficient (later only):** tune embedding model /
  clustering threshold → rely on `Revisione` → an out-of-process Python pyannote-community-1 adapter
  behind the same `Diarizzatore` port (superseding ADR; reintroduces a Python runtime).

## Consequences
- The gate needs no native libs and no weights: every ML port is tested against fakes; real-adapter
  contract tests are `@Tag("modelli")` and run via `./gradlew modelliTest` (opt-in).
- Adapter selection (which model per port) is configuration read by `:avvio`, not code in the domain.

## Amendment 2026-09-23 — `enforced_by` scoped out of `architettura-test`
**Why.** The rule was red on a clean tree for a reason unrelated to the decision: the Konsist suite
`architettura-test/src/test/kotlin/snastro/architettura/RegoleArchitetturaliTest.kt` **names** the
forbidden packages as string literals (`"com.k2fsa"`) in order to check this very confinement, and
the grep matched those literals. Approved by the user on 2026-09-23 **[user]**.

**Amended rule.** Identical, plus `--exclude-dir=architettura-test` on the `*.kt` scan.
The decision itself (what is confined, and where) is unchanged. Validated via `bash -c` on
2026-09-23: exit 0 on the tree; exit 1 with a probe `import` of a forbidden package placed in a
non-excluded module (probe removed).

**Known blind spot.** `architettura-test` itself is no longer scanned; it is a test-only module
holding the architecture rules, and its imports are reviewed by code-review.

## Amendment 2026-09-23 (b): the queue is fed by the user, not by import [user]
The Execution bullet named the policy `RegistrazioneAggiunta` → `AvviaElaborazione` as the source of
the queue. By user decision (2026-09-23, recorded in **ADR 0014**, the single home of the decision),
that policy is **removed**: the user enqueues an `Elaborazione` from S2 with "Trascrivi", or with
"Riprova" after a failure, optionally stating `NumeroPersone` (1..10). The rest of this ADR is
unchanged: the in-process execution, the single-thread dispatcher, the serial FIFO over `in_attesa`,
and the startup recovery. See also ADR 0012 Amendment (c).

## Amendment 2026-09-24 — natives: what the packaging spike answered (ADR 0016)
**Why.** The Library bullet left three questions to spike `packaging-modelli-desktop`: do Maven
coordinates exist, how are the natives placed, and how are they loaded. The spike answered them on
2026-09-24 (macOS arm64; `./gradlew run`, `.app` and `.dmg` all PASS). **ADR 0016** is the single
home of the answers. The text above is kept as written. Read it with these refinements:
- *"Whether Maven coordinates exist is verified by the packaging spike"* → **they do not.**
  sherpa-onnx comes from the k2-fsa **GitHub release v1.13.8** assets. The JVM jar and the
  per-OS `*-jni.tar.bz2` are pinned with SHA-256 in ADR 0016 §1.
- *"fetched by a Gradle task … into a build cache"* → there are **two** tasks. `scaricaJarSherpa`
  fetches the pure-Java jar for compiling, and the gate needs it. `scaricaNativiSherpa` fetches the
  two dylibs into `:avvio`'s Compose `appResourcesRootDir/<os-arch>/`, and runs only for
  `:avvio:run`, `createDistributable`, `prepareAppResources` and `modelliTest`, **never** for
  `check`. Downloads are cached in the gitignored `native-cache/` (ADR 0016 §2–3).
- *Loading* → **load them explicitly.** sherpa-onnx has no static initializer.
  `MotoreSherpa.caricaNativi()` sets `sherpa_onnx.native.path` to
  `compose.application.resources.dir` (or keeps a pre-set value, as in `modelliTest`), then calls
  `LibraryUtils.load()`. `java.library.path` is not used (ADR 0016 §4). The confinement and the
  `enforced_by` above are unchanged: this code lives in `:ml-sherpa`.
- *"macOS arm64/x64, Windows x64, Linux x64"* → only **macOS arm64** is proven and wired.
  Windows x64 and Linux x64 asset names are documented but untested. macOS x64 was not examined
  (ADR 0016 §5).
- *Execution provider* → unchanged: **CPU by default**. The spike shows CoreML is *accepted* by
  onnxruntime, not that it runs or helps. No CoreML setting is built until `benchmark-elaborazione`
  measures it against CPU (ADR 0016 §6).


## Amendment 2026-09-25 — the serial queue is shared with Sintesi ([ADR 0023](0023-coda-condivisa-elaborazioni-riassunti.md))
"One `Elaborazione` at a time from a serial FIFO queue" becomes "one item at a time, `Elaborazione` or
`Riassunto`, from ONE shared FIFO queue ordered by request instant", owned by `:avvio`
([ADR 0023](0023-coda-condivisa-elaborazioni-riassunti.md)). The pipeline itself, its dedicated
thread, the native wrappers and this ADR's `enforced_by` are unchanged. If the LLM runtime is JNI,
spike `runtime-llm-in-app`'s ADR amends this rule's `System.load` clause to admit `./llm/`
([ADR 0021](0021-sintesi-moduli-confini-porte.md) §5). Nothing else in this ADR changes.

## Amendment 2026-09-26 — native loading admitted in `./llm/` ([ADR 0026](0026-runtime-llm-jni-llama.md))
**Why.** Spike `runtime-llm-in-app` closed with the user's choice of **llama.cpp in-process via our own JNI
shim** (ADR 0026 §1). The shim needs one `System.load`, and ADR 0021 §5 reserved exactly this amendment.

**Amended rule** (the check `architettura-test/controlli-adr/adr-0004-sherpa-confinato.sh`, same name and path):
- `com.k2fsa` appears only in `ml-sherpa` — **unchanged**, and not admitted in `./llm/`;
- native loading — `System.load`, `System.loadLibrary`, and now also `Runtime.getRuntime().load*` (an idiomatic
  alternative the old rule missed) — appears only in `ml-sherpa` **and in the top-level `./llm/` module**. A
  directory named `llm` nested anywhere else (e.g. `sintesi/adattatori/…/llm/`) is not admitted.

New fixtures: `conforme/llm/…/LlamaNativo.kt` (a `System.load` + an `external fun` in `./llm/`: PASS);
`violante-k2fsa-in-llm`, `violante-llm-annidato`, `violante-runtime-load` (FAIL). Everything else in this ADR
(sherpa in-process, the serial queue, CPU provider, recovery) is unchanged; the LLM's own load/unload and
cancellation rules are ADR 0026's.

## Amendment 2026-09-26 (b) — the admitted path is the library `./llama-jni/` ([ADR 0027](0027-libreria-llama-jni-separata.md)) [user]
**Why.** The user decided that the llama.cpp binding is a separate library with no snastro dependencies
(ADR 0027). Its module is `:llama-jni` (`./llama-jni/`, package `io.github.lucolucus.llamajni`), and `:llm` is never
created.

**Amended rule** (same check, same name and path):
- native loading is admitted only in `ml-sherpa` and **the top-level `./llama-jni/`**. On Windows this means the
  library's ordered `System.load`s of llama's DLLs, then the shim (ADR 0027 §4);
- **`./llm/` is no longer admitted**, and neither is a `llama-jni` directory nested elsewhere;
- `com.k2fsa` stays `ml-sherpa`-only, and is not admitted in `./llama-jni/` either.

**Fixtures:**
- reworked: `conforme/llama-jni/…/LlamaJni.kt` (a `System.load` + `Runtime.getRuntime().load` + an `external fun`:
  PASS), `violante-k2fsa-in-llama-jni`, `violante-llama-jni-annidato`;
- new: `violante-llm-dismesso` (a `System.load` in `./llm/`: FAIL).

The library's independence from snastro is ADR 0027's own check.

