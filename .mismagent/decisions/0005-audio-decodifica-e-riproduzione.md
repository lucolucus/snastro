---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=audio --exclude-dir=build --exclude-dir=architettura-test '(org\\.bytedeco|javax\\.sound)' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q . && ! grep -rnE --include='*.kts' --include='*.toml' --exclude-dir=build 'ffmpeg[^\"]*-gpl' . | grep -q ."
amended: 2026-09-23   # see "Amendment 2026-09-23" (enforced_by scope: --exclude-dir=architettura-test)
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
