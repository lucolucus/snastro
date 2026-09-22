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
- **Native libs:** sherpa-onnx JNI + onnxruntime per OS, fetched by a Gradle task from a pinned
  release with SHA-256, cached outside the repo, never committed.
- **ffmpeg (ADR 0005):** no system ffmpeg — bytedeco FFmpeg (LGPL) bundled per OS in `:audio`;
  decode once to a derived 16 kHz mono WAV; playback via javax.sound.
- Dev machine prerequisites: JDK 21 (present: Homebrew openjdk@21; the Gradle toolchain provisions
  JBR 21), Gradle wrapper (committed by the scaffold). ffmpeg / uv / Python **not** needed.

## Model weights (ADR 0008)
- Not bundled, not in the repo. Downloaded on first run from the **k2-fsa sherpa-onnx GitHub
  releases** with **pinned SHA-256**, into the per-user cache
  (`~/Library/Application Support/snastro/modelli/`; Windows `%APPDATA%\snastro\modelli`, Linux
  `$XDG_DATA_HOME/snastro/modelli`), with a visible onboarding/progress step.
- **No HF account / token:** the sherpa export of pyannote segmentation-3.0 is re-hosted ungated
  (MIT). The draft's HF-gated onboarding step is dropped.
- After download the app works fully offline; network I/O exists only in `:modelli` (enforced_by).
  v2 (`Sintesi` via Ollama) will need a loopback-only amendment.
- In-app "Licenze dei modelli e librerie" screen (MIT/Apache-2.0/CC-BY models, LGPL FFmpeg).

## Local persistence (ADR 0006, 0007, 0010)
- One **SQLDelight + sqlite-jdbc** database per Progetto (`progetto.db`): source of truth for
  Registrazioni, Elaborazioni, Trascritti (Voci/Segmenti), Parlanti (+ ImprontaVocale), Attribuzioni.
  WAL, `foreign_keys=ON`, `secure_delete=ON`.
- **Migrations forward-only**, verified in the gate (`verifySqlDelightMigration`); committed
  schema snapshots `persistenza/src/main/sqldelight/databases/*.db` are the scoped exception to
  "never commit DB files" (schema only).
- Set invariants INV-4 / INV-16 backed by partial unique indexes (ADR 0007).
- Documenti `.md` are derived: written atomically, never read back (enforced_by, ADR 0010).

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
