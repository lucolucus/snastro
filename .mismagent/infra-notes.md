# Infra notes — snastro (desktop / single-side)

> Drafted in explore; **consolidated by the architect in model (2026-09-23)** after the user's
> deliberation — each section now cites its ADR in `decisions/`. Amend, never redraft.

## Stack & runtime
- Cross-platform desktop app, fully local: ML inference (diarization, ASR, VAD, voice-print
  embeddings) runs on-device; reference machine Apple M3 Pro 36 GB.
- **Decided (ADR 0001):** all-Kotlin — Compose Multiplatform Desktop on JetBrains Runtime 21
  (Gradle toolchain), Gradle Kotlin DSL + version catalog. **No Python** (the draft's "desktop shell
  + local Python inference engine" is superseded).
- **ML (ADR 0004):** sherpa-onnx Java/JNI **in-process**, single-thread pipeline dispatcher, serial
  `Elaborazione` queue, `AutoCloseable` native wrappers, CPU execution provider by default (CoreML
  opt-in only if a spike measures a gain). Child JVM = documented escape hatch only.
- *(2026-09-25, [ADR 0021](decisions/0021-sintesi-moduli-confini-porte.md)/[0023](decisions/0023-coda-condivisa-elaborazioni-riassunti.md))* **Local LLM (Sintesi):** runtime in the technical module `:llm`, llama.cpp JNI or a
  `llama-server` sidecar, **OPEN**: spike `runtime-llm-in-app`, whose ADR pins its natives per ADR 0016 (build-time
  fetch, SHA-256, never downloaded by the app) and the Metal use. It runs on the SHARED serial queue, one item at
  a time with the `Elaborazione`s, is unloaded after each `Riassunto` by default, and never takes the sherpa Mutex.
  NFR: ≤ 300 s for a 60-min `Registrazione` on the M3 Pro (opt-in, outside the gate).
  → *(amended 2026-09-26, [ADR 0026](decisions/0026-runtime-llm-jni-llama.md), spike closed [user])* **llama.cpp b11195
  in-process via our own JNI shim** in `:llm` (no sidecar, no loopback: ADR 0008 untouched; ADR 0004 admits
  `System.load` in `./llm/`). Natives: pinned release assets + SHA-256, `scaricaNativiLlama` + `compilaShimLlama`
  (clang, outside the gate) into `:avvio`'s app resources; Metal all layers, `n_ctx` 40 960, unloaded after every
  run. **NFR amended:** a 60-min `Registrazione` at the default 2000-word cap in **≤ 600 s** (`benchmarkRiassunto`,
  opt-in); typical ≈ 3–4 min, up to ≈ 8–10 min near the 2500-word cap. Windows/Linux (dynamic ggml backends, Vulkan,
  per-OS shim build) open and untested; Developer ID signing (ADR 0016 O-2) must cover the llama/ggml dylibs and the shim.
- **Native libs:** sherpa-onnx JNI + onnxruntime per OS, fetched by a Gradle task from a pinned
  release with SHA-256, cached outside the repo, never committed.
  → pinned by **ADR 0016** (2026-09-24): v1.13.8 GitHub release assets (not Maven Central);
  `scaricaJarSherpa` (compile, gate) + `scaricaNativiSherpa` (run / distributable / `modelliTest`,
  never `check`) into gitignored `native-cache/` and `:avvio`'s Compose `appResourcesRootDir/macos-arm64/`;
  explicit `LibraryUtils.load()` via `sherpa_onnx.native.path`.
- **ffmpeg (ADR 0005):** no system ffmpeg — bytedeco FFmpeg (LGPL) bundled per OS in `:audio`;
  decode once to a derived 16 kHz mono WAV; playback via javax.sound.
- Dev machine prerequisites: JDK 21 (present: Homebrew openjdk@21; the Gradle toolchain provisions
  JBR 21), Gradle wrapper (committed by the scaffold). ffmpeg / uv / Python **not** needed.

## Model weights (ADR 0008)
- Not bundled, not in the repo. Downloaded on first run from the **k2-fsa sherpa-onnx GitHub
  releases** with **pinned SHA-256**, into the per-user cache
  (`~/Library/Application Support/snastro/modelli/`; Windows `%LOCALAPPDATA%\snastro\modelli` — non-roaming,
  ADR 0008 Amendment (c) —, Linux `$XDG_DATA_HOME/snastro/modelli`, never the XDG cache), with a visible
  onboarding/progress step. Each catalogue entry is an archive (`.tar.bz2`) or single file, SHA-256 verified,
  extracted into `<cartella>/<id>/` (Apache Commons Compress, Apache-2.0, `:modelli` only).
- **No HF account / token:** the sherpa export of pyannote segmentation-3.0 is re-hosted ungated
  (MIT). The draft's HF-gated onboarding step is dropped.
- After download the app works fully offline; network I/O exists only in `:modelli` (enforced_by).
  v2 (`Sintesi` via Ollama) will need a loopback-only amendment.
- In-app "Licenze dei modelli e librerie" screen (MIT/Apache-2.0/CC-BY models, LGPL FFmpeg).
- *(amended 2026-09-25, [ADR 0025](decisions/0025-modello-facoltativo-su-richiesta.md))* **Optional model, downloaded on demand:** Sintesi's LLM (Qwen3.5 9B
  q4_K_M GGUF, Apache-2.0, 6.6 GB) is a catalogue entry with `obbligatoria = false`. It is not part of onboarding, and
  only the user downloads it, from the Riassunto tab. The host is an ungated, immutable HTTPS URL (Hugging Face
  `resolve/<commit>`), with SHA-256 pinned. A single-file asset is **moved**, not copied, into place (peak disk use =
  1× its size), and free space is checked first. *(2026-09-26, ADR 0026 §8)* Entry `llm-qwen3.5-9b-q4_k_m`: bartowski
  Q4_K_M at HF commit `182be2fd…`, 6 169 341 984 B (**6,2 GB**, not 6.6), SHA-256 `d784ce9e…`. The "v2 Ollama loopback" note above is superseded: no user-installed
  Ollama. Whether a loopback client exists at all is spike `runtime-llm-in-app`.

## Local persistence (ADR 0006, 0007, 0010)
- One **SQLDelight + sqlite-jdbc** database per Progetto (`progetto.db`): source of truth for
  Registrazioni, Elaborazioni, Trascritti (Voci/Segmenti), Parlanti (+ ImprontaVocale), Attribuzioni.
  WAL, `foreign_keys=ON`, `secure_delete=ON`.
- **Migrations forward-only**, verified in the gate by the `:persistenza:test` migration test
  (ADR 0006 Amendment (a)); a committed `.sqm` is never edited (a fix is a new migration), no down
  migrations.
- Set invariants INV-4 / INV-16 backed by partial unique indexes (ADR 0007).
- Documenti `.md` are derived: written atomically, never read back (enforced_by, ADR 0010).
- *(2026-09-25, [ADR 0022](decisions/0022-persistenza-sintesi-6sqm.md))* `Riassunto`s live only in `progetto.db` (`6.sqm`): plain TEXT, one row per element and
  per `Fonte` (FTS5-ready), no `Nome`/`ParlanteId` stored. They are included in the folder backup and deleted with their
  `Registrazione` (ADR 0024).

## Privacy (biometric data) (ADR 0009)
- `ImprontaVocale` stored only as BLOB rows in the project DB; no other copy (no file, cache, log).
- `Eliminazione del Parlante` purges every print in the same transaction as the tombstone, then a
  WAL checkpoint; `secure_delete=ON`.
- No in-app encryption (rely on FileVault). **User-accepted caveat:** backups/copies of the project
  folder made outside the app may retain purged prints — documented, not promised away.
- Never commit audio samples, model weights, project folders (`.gitignore`).

## Project layout, backup & restore (ADR 0010)
- A Progetto is a **self-contained relocatable folder** `<nome>.snastro/` (default parent
  `~/Documents/snastro/`): `progetto.db`, `audio/` (**copied** sources), `documenti/`
  (`<AAAA-MM-DD> <titolo>.md`, atomic overwrite), `cache/audio/` (derived WAV, regenerable), `.lock`.
- Paths in the DB are relative → the folder can be copied/moved/backed up as a unit.
- Backup = the user copies the folder; the app makes no backups. Retention: none.
- One project open per app instance (file lock).

## Packaging & distribution
- **v1:** only on the user's Mac, run from source — `./gradlew :avvio:run` — unsigned.
- **Later (infra block):** Compose Gradle plugin jpackage `.dmg` (bundled JRE; signed natives:
  sherpa JNI, onnxruntime, FFmpeg dylibs), signing/notarization only if an Apple Developer account
  is used; Conveyor is the option if cross-building/updates are ever wanted.
- **Proven 2026-09-24 (ADR 0016):** an unsigned `.dmg` built with Compose `packageDmg` runs on this Mac,
  with the sherpa natives, FFmpeg, VAD, embeddings and playback all working. **OPEN [user] before a distributable
  `.dmg`:** O-1 which JDK the package bundles (JBR / Corretto / vendor check disabled: Compose refuses
  Homebrew OpenJDK), and O-2 signing and notarization. Neither blocks R1, which runs from source.
- Windows/Linux: kept open architecturally (per-OS native classifiers, OS-appropriate dirs) but
  **not built or tested in v1**.

## App updates
- None in v1 (git pull + run). Migrations forward-only so a later update mechanism never corrupts
  existing projects; a DB newer than the app refuses to open with a clear message.

## Performance (ADR 0011)
- NFR: 1 h of audio processed in ≤ 10 min, **measured only on the M3 Pro**, models downloaded, real
  60-min sample, via the opt-in `./gradlew benchmarkElaborazione -Pcampione=<path>`. Other
  platforms best-effort.

## Needs → work
- Wave-0 scaffold: Gradle multi-module skeleton per `architecture.md`, version catalog, toolchain,
  detekt/Konsist/`verificaDipendenzeModuli` wiring, SQLDelight plugin, native-lib fetch task,
  `modelliTest` + `benchmarkElaborazione` + `:ui:renderCheck` tasks → `scaffold` block.
- Native libs fetch (pinned + SHA-256) → scaffold / packaging spike.
- Model catalogue + first-run download + licence screen → `:modelli` blocks.
- Project folder layout + lock + copy of sources + atomic `.md` writer → blocks.
- Voice-print purge → invariant test + adapter round-trip test (ADR 0009).
- `.gitignore` (done at bootstrap, 2026-09-23).
- macOS `.dmg` packaging → later infra block.
