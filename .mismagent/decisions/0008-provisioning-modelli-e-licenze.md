---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=modelli --exclude-dir=build '(java\\.net\\.|io\\.ktor|okhttp3|HttpClient|HttpURLConnection)' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
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
