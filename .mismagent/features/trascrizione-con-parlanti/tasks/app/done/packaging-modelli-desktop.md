---
id: packaging-modelli-desktop-spike
type: spike
side: app
repo: .
depends_on: []
status: answered
closed_by: 0016-packaging-nativi-sherpa
closed: 2026-09-24 (evidence: research/spike-packaging-modelli-desktop.md; open [user] decisions O-1 JDK, O-2 signing do not block closure)
---
# Spike / Hello-world: sherpa-onnx JNI + bytedeco FFmpeg inside a Compose Desktop app (run + jpackage)

> Re-scoped by the architect on 2026-09-23 (ADR 0001/0004/0005/0008): no Python; the question is
> now how the JVM app loads per-OS native libraries and first-run models.

## Question to answer
Can a minimal Compose Desktop app (Kotlin/JBR 21) (1) load the sherpa-onnx JNI + onnxruntime native
libraries fetched by a Gradle task from a pinned release (SHA-256 verified) — both via
`./gradlew run` and from a jpackage `.dmg` (natives in app resources, signed if signing is used) —,
(2) decode an `.m4a` from `sample/` with bytedeco FFmpeg (LGPL build) to a 16 kHz mono WAV,
(3) run Silero VAD + one speaker-embedding extraction on it, and (4) play it from an offset with
javax.sound? Also: is the sherpa-onnx Java API available as Maven coordinates or only as a release
jar; does the CoreML execution provider load.

## Closure criterion
The hello-world runs on macOS (M3 Pro) both from `./gradlew run` and from the `.dmg`; the
Windows (`.dll`) / Linux (`.so`) paths for the natives and the model cache are documented; an ADR
records the native-lib fetch coordinates/checksums and the jpackage resource layout (amending ADR
0004/0005 if needed).

## Unblocks
(block ids pinned by build-manifest) the wave-0 `scaffold` block of side `app` (native fetch task);
every ML adapter in `:ml-sherpa`, the `:audio` adapters, the later `.dmg` infra block.
