---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=build '(java\\.net\\.|io\\.ktor|okhttp3|HttpClient|HttpURLConnection)' . | grep -vE '^(\\./)?(modelli|architettura-test)/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
amended: 2026-09-23   # see "Amendment 2026-09-23 (b)" (enforced_by scope) + "Amendment 2026-09-23 (c)" (Windows LOCALAPPDATA, archive entries, enforced_by module-path scoping)
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
  (Windows `%APPDATA%\snastro\modelli` — *superseded by Amendment (c): `%LOCALAPPDATA%`* —, Linux `$XDG_DATA_HOME/snastro/modelli`). Never in a project
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

## Amendment 2026-09-23 (c) — models directory per OS, archive catalogue entries, `enforced_by` scoped to the module path
**Why.** The code-review of block `modelli-provisioning` (cycle 0) surfaced two decisions and the
verifier one rule blind spot. The user decided (1) and (2) on 2026-09-23 **[user]** (dispatch.log
`(modelli) decision`); (3) is the mechanical fix of a false-green risk.

### (1) Models directory — Windows uses `%LOCALAPPDATA%` (supersedes the `%APPDATA%` bullet)
- **Windows:** `%LOCALAPPDATA%\snastro\modelli` (non-roaming: GBs of re-downloadable data must never
  travel with a roaming profile); fallback `<user.home>\AppData\Local\snastro\modelli`.
- **macOS:** unchanged, `~/Library/Application Support/snastro/modelli/`.
- **Linux:** unchanged, `$XDG_DATA_HOME/snastro/modelli` with fallback `~/.local/share/snastro/modelli`.
  **Not** `XDG_CACHE_HOME`: cache cleaners would wipe GBs and force a re-download.
- **Environment values that are blank or RELATIVE are treated as unset** (XDG Base Directory spec:
  "if a relative path is set, it is invalid and should be ignored"), applied to both `LOCALAPPDATA`
  and `XDG_DATA_HOME` → the fallback is used.

### (2) A catalogue entry is an ARCHIVE; `percorso(id)` is a DIRECTORY
- `VoceCatalogo` describes the **downloaded asset**: `url`, `sha256` **of the asset**, `dimensioneByte`
  **of the asset**, `formato`, plus the existing id / ruolo / licenza / attribuzione.
- **Supported `formato`s:** `TAR_BZ2` (the k2-fsa releases: a `.tar.bz2` holding
  encoder/decoder/joiner/tokens or model.onnx/tokens) and `FILE` (a single asset, e.g.
  `silero_vad.onnx` or a speaker-embedding `.onnx`, which k2-fsa publishes un-archived). Nothing else in
  v1; a new format is an amendment.
- **Install protocol:** download to `<cartella>/<id>.part` (resumable) → verify the asset's SHA-256
  (and size) → extract (`TAR_BZ2`) or copy (`FILE`, keeping its file name) into a temp directory
  **next to the target** (`<cartella>/<id>.tmp-<n>/`, same file system) → write the marker
  `<id>.tmp-<n>/.sha256` (the installed asset hash) → **atomic directory rename** to `<cartella>/<id>/`
  → delete the `.part`. If an old `<id>/` exists it is first renamed aside and deleted after the
  swap. Leftover `*.tmp-*` directories are swept at start.
- **Extraction rules:** if every archive entry sits under ONE top-level directory, that component is
  stripped (so files land at `<id>/encoder.int8.onnx`); entries whose normalized path escapes the
  target (absolute, `..`), symlinks and hard links are **rejected** (the install fails with a disk
  error, nothing is renamed into place).
- `percorso(id)` = `<cartella>/<id>/` (the directory); the adapters (`ml-sherpa-*`) resolve their
  file names inside it. `installata(id)` = directory present **and** `.sha256` equal to the catalogue's
  sha256 → a catalogue hash change triggers a re-install.
- **Any SHA-256 change of an entry MINTS A NEW `id`** (never reuse an id for different bytes): the
  embedding model's id is `EstrattoreImpronta.modello`, and ADR 0012 Amendment (b)'s staleness rule
  (`modello_impronta ≠ EstrattoreImpronta.modello`) only works if different weights never share an id.
- **Extraction library: Apache Commons Compress** (`org.apache.commons:commons-compress`, **Apache-2.0**,
  pinned at 1.28.0 or the latest stable 1.x at pin time; it brings commons-io + commons-lang3, both
  Apache-2.0), used **only in `:modelli`** for `BZip2CompressorInputStream` + `TarArchiveInputStream`.
  The JDK has no bzip2 decoder and no tar reader; Commons Compress is small, permissive and needs no
  native code. It goes in the version catalog and in the "Licenze dei modelli e librerie" list.

### (3) `enforced_by` scoped to the module PATH, not to every directory named `modelli`
`--exclude-dir=modelli` excluded **every** directory called `modelli` — a future
`ui/src/main/kotlin/snastro/ui/modelli/` package would have escaped the network scan. The rule now
scans all `*.kt` (still skipping `build/` output) and drops only lines whose path starts with the
module roots `./modelli/` or `./architettura-test/` (the latter kept from Amendment (b), now scoped
the same way). Validated via `bash -c` on 2026-09-23 from the repo root: exit 0 on the tree; exit 1
with a probe `import java.net.URI` in `ui/src/main/kotlin/snastro/ui/modelli/ProbeRete.kt` (caught
— the old rule would have missed it); exit 0 with the same probe under
`modelli/src/main/kotlin/snastro/modelli/` (allowed); probes removed.

**Known blind spot (unchanged).** `--exclude-dir=build` still skips any directory named `build`;
Kotlin packages named `build` are not used in this codebase.

### Consequences
- Block `modelli-provisioning` (rework queued) gains the archive/extraction/marker, LOCALAPPDATA,
  blank/relative-env criteria; `ml-sherpa-*` adapters take a directory from `percorso(id)`.
- `:modelli` gains one runtime dependency (Commons Compress); no other module may depend on it.
