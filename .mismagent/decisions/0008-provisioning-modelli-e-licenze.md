---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=modelli --exclude-dir=build --exclude-dir=architettura-test '(java\\.net\\.|io\\.ktor|okhttp3|HttpClient|HttpURLConnection)' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
amended: 2026-09-23   # see "Amendment 2026-09-23 (b)" (enforced_by scope: --exclude-dir=architettura-test)
---
# 0008 — Model provisioning: first-run download from k2-fsa releases, pinned SHA-256, offline after; network only in `:modelli`

## Context
Weights (~0.7 GB with Parakeet v3 int8; +~1 GB if Whisper turbo is kept) must not be bundled in the
repo. Everything must work offline after setup (no audio or voice-print ever leaves the machine).
pyannote segmentation-3.0 is HF-gated on Hugging Face; the sherpa-onnx ONNX export is re-hosted by
k2-fsa on GitHub releases **without a gate** and its licence is **MIT** (the HF gate collects user
info, it is not a licence restriction). pyannote community-1 (CC-BY-4.0, gated) is not available
in sherpa.

## Decision
- `:modelli` holds the **model catalogue** (id, role, URL on the k2-fsa sherpa-onnx GitHub releases —
  `asr-models`, `speaker-segmentation-models`, `speaker-recongition-models`, VAD —, file list,
  **pinned SHA-256**, size, **licence + attribution**) and the **first-run download** (progress,
  resumable, verify-then-atomic-move). **No HF account, no token.**
- Cache: per-user, shared by all projects — macOS `~/Library/Application Support/snastro/modelli/`
  (Windows `%APPDATA%\snastro\modelli`, Linux `$XDG_DATA_HOME/snastro/modelli`). Never in a project
  folder, never in the repo.
- **Network I/O exists ONLY in `:modelli`** (enforced_by). After the models are present the app makes
  no network call; the inference path never downloads. **Amendment reserved for v2:** a
  **loopback-only** client (Ollama for `Sintesi`) will be allowed by a superseding/amending ADR.
- An in-app **"Licenze dei modelli e librerie"** screen lists each model's licence and attribution
  (MIT pyannote segmentation, Apache-2.0/CC-BY models, LGPL FFmpeg, sherpa-onnx Apache-2.0,
  onnxruntime MIT), generated from the catalogue.
- Startup: if required models are missing/corrupt (hash mismatch) → the onboarding screen; any
  `Elaborazione` waits (`in_attesa`) until models are ready.

## Consequences
- First run needs network once; later runs are fully offline.
- Changing a model = a catalogue edit (URL + SHA-256 + licence) + the spike/ADR that chose it.

## Amendment 2026-09-23 (build-manifest reconciliation R10 — user decision: include in v1)
The onboarding/download screen and the "Licenze dei modelli e librerie" list are one v1 screen,
**S5 · Modelli** (ui block `schermata-modelli`, added to the ux-proposal): download with progress,
hash-error and no-network states with "Riprova", licences from the catalogue. `:ui` cannot depend on
`:modelli`, so S5 declares a `ServizioModelli` port implemented in `:avvio` over `:modelli`
(block `avvio-composizione`); the mechanism itself is block `modelli-provisioning`, and each
spike ADR adds its catalogue entry in the real-adapter block it gates.

## Amendment 2026-09-23 (b) — `enforced_by` scoped out of `architettura-test`
**Why.** The rule was red on a clean tree for a reason unrelated to the decision: the Konsist suite
`architettura-test/src/test/kotlin/snastro/architettura/RegoleArchitetturaliTest.kt` **names** the
forbidden packages as string literals (`"java.net."`, `"io.ktor"`, `"okhttp3"`) in order to check this very confinement, and
the grep matched those literals. Approved by the user on 2026-09-23 **[user]**.

**Amended rule.** Identical, plus `--exclude-dir=architettura-test` on the `*.kt` scan.
The decision itself (what is confined, and where) is unchanged. Validated via `bash -c` on
2026-09-23: exit 0 on the tree; exit 1 with a probe `import` of a forbidden package placed in a
non-excluded module (probe removed).

**Known blind spot.** `architettura-test` itself is no longer scanned; it is a test-only module
holding the architecture rules, and its imports are reviewed by code-review.
