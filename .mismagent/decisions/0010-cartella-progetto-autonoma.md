---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=build '(readText|readLines|readBytes|readAllBytes|readAllLines|readString|bufferedReader|inputStream|FileReader|Files\\.lines|Files\\.newBufferedReader|Files\\.newInputStream)' documento | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
---
# 0010 — The Progetto is a self-contained, relocatable folder; `Documento` is written, never read back

## Context
User answers (2026-09-23): project = self-contained relocatable folder; source audio **copied**;
`.md` inside the project; one project open per instance; retention none; v1 only on the user's Mac,
run from source, unsigned, no auto-update.

## Decision
- Layout (default parent `~/Documents/snastro/`; the user can create/open a project anywhere):
  ```
  <nome>.snastro/
    progetto.db            source of truth (ADR 0006), incl. ImprontaVocale (ADR 0009)
    audio/                 COPIED source files, one per Registrazione (<registrazioneId>.<ext>)
    documenti/             Documento .md, named "<AAAA-MM-DD> <titolo>.md" (DataRegistrazione + titolo)
    cache/audio/           derived 16 kHz WAV per Registrazione (ADR 0005) — regenerable, deletable
    .lock                  single-instance lock
  ```
- All paths stored in the DB are **relative to the project folder** — moving/copying the folder
  keeps it working.
- `AggiungiRegistrazione` copies the source into `audio/` (verify size, then commit the row);
  a failed copy creates nothing.
- **`Documento`** is written with **atomic overwrite** (write `*.tmp` in `documenti/`, then atomic
  move/replace) on every `Rigenerazione`; the `.md` is **never read back** by the app ([INV-23]) —
  enforced_by forbids read APIs in the `documento` modules. If `DataRegistrazione` or the title
  changes the file name, the old file is removed after the new one is in place.
- **Lock:** opening a project takes an OS file lock on `.lock` (`FileChannel.tryLock`); a second
  instance gets a clear "progetto già aperto" message. One project open per app instance.
- **Retention:** none; a project lives until the user deletes its folder.
- **Distribution v1:** run from source on the user's Mac (`./gradlew :avvio:run`), unsigned, no
  auto-update; the jpackage `.dmg` is a later infra block. Windows/Linux are kept open
  architecturally (per-OS natives in ADR 0004/0005, OS-appropriate dirs in ADR 0008) but not built
  or tested in v1.

## Consequences
- Disk per hour of audio ≈ source (30–60 MB m4a) + derived WAV (≈115 MB, regenerable).
- Project folders are git-ignored (`*.snastro/`).
