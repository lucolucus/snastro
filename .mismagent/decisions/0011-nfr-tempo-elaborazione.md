---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: null
---
# 0011 — NFR: 1 h of audio processed in ≤ 10 min on the M3 Pro reference machine

## Context
The user set the target at ≤ 10 min per hour of audio (stricter than the proposed 20) and, after
seeing that "everywhere" would be unverifiable and would eliminate every Whisper variant on CPU,
chose **"Solo M3 Pro"**: the target binds only the reference machine; other platforms are
best-effort.

## Decision
- **Measurable AC:** on the **Apple M3 Pro, 36 GB** (the user's machine), for a **real 60-minute
  sample** from `sample/`, with **models already downloaded** and the app idle otherwise, the
  wall-clock time of one `Elaborazione` from `in_corso` to `completata` (all `FaseElaborazione`:
  `decodifica`, `diarizzazione`, `trascrizione`, `allineamento`, model loading included) is
  **≤ 600 s**.
- Verified by the **opt-in** Gradle task `./gradlew benchmarkElaborazione -Pcampione=<path>`
  (runs the real adapters, prints per-phase timings, fails above 600 s). Not part of `./gradlew
  check` (needs weights + a real sample); run by the ML spikes, when an ML adapter changes, and
  before a release.
- Execution provider: CPU by default; CoreML is enabled only if a spike shows it is needed/helps to
  meet this target on the M3 Pro (ADR 0004).
- **Consequence for the spikes:** Whisper large-v3-turbo ONNX (possibly with CoreML) stays a
  candidate next to Parakeet TDT 0.6B v3 int8 — both are measured against this AC; heavy
  embedding models are measured too (embedding extraction over diarization windows is a large share).

## Consequences
- Discursive/measurable: no grep can check a time budget; the benchmark task is the guardian, and
  the build-manifest attaches it as an AC of the avvia-elaborazione block (opt-in run).
