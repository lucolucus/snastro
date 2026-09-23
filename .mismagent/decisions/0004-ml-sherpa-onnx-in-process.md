---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=ml-sherpa --exclude-dir=build --exclude-dir=architettura-test '(com\\.k2fsa|System\\.load)' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
amended: 2026-09-23   # see "Amendment 2026-09-23" (enforced_by scope: --exclude-dir=architettura-test) and "Amendment 2026-09-23 (b)" (no automatic start, ADR 0014 [user])
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
