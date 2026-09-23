---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=build '(org\\.bytedeco|javax\\.sound)' . | grep -vE '^(\\./)?(audio|architettura-test)/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q . && ! grep -rniE --include='*.kts' --include='*.toml' --include='*.gradle' --exclude-dir=build '(-|_|\")gpl' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*|#)' | grep -q ."
amended: 2026-09-24   # see "Amendment 2026-09-23" (enforced_by scope: architettura-test) + "Amendment 2026-09-23 (b)" (-gpl clause blind spot; module-path scoping) + "Amendment 2026-09-24" (packaging spike evidence, ADR 0016)
---
# 0005 — Audio: decode once with bytedeco FFmpeg (LGPL) to a derived WAV; play via javax.sound

## Context
Sources are `.m4a` (AAC) and other formats the user drops in. The ML needs 16 kHz mono float
samples; the UI must play from an exact `Segmento` / `EstrattoAudio` offset. No system ffmpeg on
the dev machine. Options weighed in pass-1: bytedeco FFmpeg (JVM binding with bundled natives) ·
pure-JVM AAC decoders (JCodec/JAAD — incomplete HE-AAC, abandoned) · a bundled `ffmpeg` executable
(extra binary to sign/notarize per OS, static builds usually GPL, process plumbing) · JavaFX Media
(AAC on Linux depends on system libav; second UI toolkit) · vlcj (VLC install, GPL).

## Decision
- **`org.bytedeco:ffmpeg` (LGPL build — never the `-gpl` artifacts) via javacv**, one per-OS
  classifier per target, confined to **`:audio`** (enforced_by).
- `:audio` provides: **`sonda`** (readability + duration at `AggiungiRegistrazione`; unreadable or
  unsupported → the command returns an `Esito.Errore` and nothing is created) and **`decodifica`**
  (decode + resample once to **16 kHz mono 16-bit PCM WAV** at
  `<progetto>/cache/audio/<registrazioneId>.wav`, ≈115 MB per hour; the `decodifica`
  `FaseElaborazione`). The WAV is **derived and regenerable** from the copied source
  (`<progetto>/audio/`, ADR 0010).
- **Playback:** `javax.sound.sampled` (`SourceDataLine`) on the derived WAV, seeking by exact
  sample offset (`inizioMs × 16`). `:audio` exposes a `RiproduttoreWav`; `:avvio` adapts it to the
  `LettoreAudio` interface declared in `:ui` (`:audio` never depends on `:ui`). If the derived WAV is missing it is rebuilt from the source; if the source is missing
  too → the "Audio source missing" state of S3 (transcript still usable).
- The ML reads the same WAV (one decoder in the whole system).

## Consequences
- ~20–40 MB of FFmpeg natives per OS; no system dependency.
- Playback of a new `Registrazione` is available only after the `decodifica` phase (seconds).
- LGPL obligations: dynamic linking (bytedeco ships shared libs) + licence notice in the app's
  licences screen (ADR 0008).

## Amendment 2026-09-23 — `enforced_by` scoped out of `architettura-test`
**Why.** The rule was red on a clean tree for a reason unrelated to the decision: the Konsist suite
`architettura-test/src/test/kotlin/snastro/architettura/RegoleArchitetturaliTest.kt` **names** the
forbidden packages as string literals (`"org.bytedeco"`, `"javax.sound"`) in order to check this very confinement, and
the grep matched those literals. Approved by the user on 2026-09-23 **[user]**.

**Amended rule.** Identical, plus `--exclude-dir=architettura-test` on the `*.kt` scan. The second clause (`-gpl` artifacts in `*.kts`/`*.toml`) is unchanged: it never matched that module.
The decision itself (what is confined, and where) is unchanged. Validated via `bash -c` on
2026-09-23: exit 0 on the tree; exit 1 with a probe `import` of a forbidden package placed in a
non-excluded module (probe removed).

**Known blind spot.** `architettura-test` itself is no longer scanned; it is a test-only module
holding the architecture rules, and its imports are reviewed by code-review.

## Amendment 2026-09-23 (b) — `-gpl` clause blind spot; module-path scoping
**Why.** The verifier of block `audio-ffmpeg` found that the second clause (`ffmpeg[^"]*-gpl`) needs
the word `ffmpeg` on the same line as `-gpl`, but `audio/build.gradle.kts` builds the FFmpeg
classifier as a string (`"$piattaforma-$architettura"`) in a helper function: a `-gpl` suffix appended
there (`"$piattaforma-$architettura-gpl"`) would pass unnoticed. Separately, `--exclude-dir=audio` in
the first clause excluded **every** directory named `audio` (a future `snastro/ui/audio/` package
would escape) — the same blind spot fixed in ADR 0008 Amendment (c).

**Amended rule.** The decision is unchanged; only the guardian is re-scoped:
- *Clause 1* (confinement of `org.bytedeco` / `javax.sound`): scans all `*.kt` except `build/`
  output, then drops only lines whose path starts with the module roots `./audio/` or
  `./architettura-test/`.
- *Clause 2* (never a GPL build): forbids the token `gpl` preceded by `-`, `_` or a quote —
  case-insensitive — in ANY non-comment line of any `*.kts`, `*.toml`, `*.gradle` file (build
  scripts, version catalog, `build-logic`), whatever the line talks about. This covers an artifact
  name, a classifier literal, a string template suffix and a `"gpl"` literal concatenated in pieces; it
  does not match `LGPL`/`lgpl` (the preceding character is `l`). Comment lines (`//`, `*`, `/*`, `#`)
  are stripped, so the prose documenting the rule stays legal.

**Validated via `bash -c` on 2026-09-23 from the repo root:** exit 0 on the tree (the only
`-gpl` mentions are the comment lines in `audio/build.gradle.kts` and `gradle/libs.versions.toml`);
exit 1 with a probe `val probe = "$piattaforma-$architettura-gpl"` appended to a scratch `*.kts` in
`audio/` (the case the old rule missed); exit 1 with a probe `import org.bytedeco.ffmpeg.global.avutil`
in `ui/src/main/kotlin/snastro/ui/audio/`; exit 0 with that import under `audio/src/`; probes removed.

**Known blind spots.** A classifier assembled from a variable holding `gpl` with no preceding
`-`/`_`/quote on the same line (e.g. `listOf("x", "gp" + "l")`) is not caught — deliberate
obfuscation is left to code-review; `--exclude-dir=build` skips any directory named `build`.

## Amendment 2026-09-24 — packaging spike evidence (ADR 0016)
**Why.** Spike `packaging-modelli-desktop` exercised this ADR's decode and playback path from
`./gradlew run`, the `.app` and the mounted `.dmg` on macOS arm64. All passed. The decision is
**unchanged**. The spike refines two points; the text above is kept as written:
- **Decode recipe confirmed.** javacv `FFmpegFrameGrabber` with an FFmpeg `aformat` filter
  produces the 16 kHz mono WAV. `SourceDataLine` plays it from an offset. The spike used synthetic
  audio (an m4a written by `FFmpegFrameRecorder`); real `sample/` recordings are exercised by the
  `audio-ffmpeg` block and R1.
- **No extra native layout for FFmpeg.** The bytedeco natives travel in their classifier jars on
  the classpath. Unlike sherpa-onnx (ADR 0016 §3), they need no `appResourcesRootDir` entry to
  load from the packaged app.
- **For the later distributable `.dmg`:** these FFmpeg dylibs are nested Mach-O files *inside
  jars*. If the user chooses Developer ID signing and notarization (ADR 0016 open decision O-2),
  they must be signed too. This is not verified.

