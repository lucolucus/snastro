---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! find . -name '*.py' -not -path './.mismagent/*' -not -path '*/build/*' -not -path './.gradle/*' | grep -q ."
---
# 0001 — Stack: all-Kotlin desktop (Compose Multiplatform, JetBrains Runtime 21), no Python

## Context
snastro is a cross-platform desktop app (macOS first, nothing Mac-only) that runs diarization,
speech-to-text and voice-print extraction **locally**, plays `.m4a` from a `Segmento` offset and
keeps a local source of truth. Build is full-agentic (Claude builds, the user reviews), so the gate
must run headlessly. Deliberation (architect pass-1, three iterations, 2026-09-23):
1. all-Python (PySide6) — rejected by the user;
2. Kotlin + Python ML sidecar (JSON-RPC over stdio) — rejected: 2 languages, an IPC seam on every
   ML port, Python runtime provisioning;
3. **all-Kotlin with the ML in-process on the JVM via sherpa-onnx** — **chosen [user]**, following
   the user's reference app heimeshoff/Whisperheim (.NET + sherpa-onnx, same model family).

## Decision
- **Language / runtime:** Kotlin 2.x on **JetBrains Runtime 21**, provisioned by the Gradle
  toolchain (foojay resolver). Coroutines + Flow for background work and progress.
- **UI:** Compose Multiplatform **Desktop** (Skia), Material 3.
- **Build:** Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`), Gradle wrapper
  committed; multi-module (module map in `architecture.md`, ADR 0002).
- **Tests:** kotlin.test on JUnit 5; Compose UI tests (`runComposeUiTest`) headless; Konsist
  (architecture); detekt (style).
- **ML:** sherpa-onnx Java/JNI in-process → ADR 0004. **Audio:** bytedeco FFmpeg + javax.sound →
  ADR 0005. **Persistence:** SQLDelight + sqlite-jdbc → ADR 0006.
- **No Python anywhere in the product codebase** (the Python pyannote adapter remains only a
  *later* fallback behind the `Diarizzatore` port, which would need a superseding ADR).
- **Packaging:** Compose Gradle plugin (jpackage) `.dmg` — later; v1 runs from source
  (`./gradlew :avvio:run`), unsigned, macOS only (ADR 0010, infra-notes).

## Consequences
- One language, one toolchain, one gate (`./gradlew check`), one dev-architecture memory.
- Module boundaries are compiler-enforced (Gradle module graph) — stronger than an import linter.
- The ML is limited to what sherpa-onnx exports (no pyannote community-1, no Sortformer, no MLX);
  the spikes are re-scoped accordingly.
- `enforced_by` guards "no Python" (spike measurement scripts, if any, live outside the repo).
