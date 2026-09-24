# Spike packaging-modelli-desktop — results (2026-09-24, macOS arm64 M3 Pro)
Scratch hello-world (Compose Desktop, Kotlin/JVM 21), repo untouched; synthetic audio only.

## sherpa-onnx (v1.13.8) — NOT on Maven Central; GitHub release assets
- `sherpa-onnx-jvm-1.13.8.jar` — https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar — SHA-256 `77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b`
- `sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2` — …/v1.13.8/sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2 — SHA-256 `2505fd9bd46a28615ab3a14833caae8479c80993658402d57a949d7403c931ad`; only `lib/libonnxruntime.dylib` + `lib/libsherpa-onnx-jni.dylib` needed (otool -L: jni → onnxruntime + system libs).
- Alternative `sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar` carries byte-identical dylibs (classpath loader) — not used.
- Windows x64 (documented, untested): `sherpa-onnx-v1.13.8-win-x64-jni.tar.bz2` → `onnxruntime.dll`, `sherpa-onnx-jni.dll` (likely under `bin/` — verify tar members). Linux x64: `sherpa-onnx-v1.13.8-linux-x64-jni.tar.bz2` → `lib/libonnxruntime.so`, `lib/libsherpa-onnx-jni.so`.

## Loading
`LibraryUtils.load()` must be called explicitly (no static initializer) after setting system property `sherpa_onnx.native.path` to a flat dir with both libs; it `System.load`s onnxruntime then jni. No java.library.path. Idempotent; the execution provider is per-instance config.

## jpackage layout (Compose `nativeDistributions.appResourcesRootDir`)
`resources/macos-arm64/*.dylib` (os-arch) + `resources/common/...` (shared). At runtime `System.getProperty("compose.application.resources.dir")` = flattened merge of common + current os-arch (the `common/` prefix is stripped); in the .app it is `Contents/app/resources/`. A `fetchSherpaOnnxNatives` task (download + SHA-256 verify + extract into `resources/macos-arm64/`) must be a dependency of `run`, `createDistributable` and `prepareAppResources` (Gradle validation fails otherwise).

## Results (./gradlew run, .app binary, binary from mounted .dmg — all PASS)
1 natives load · 2 m4a (javacv FFmpegFrameRecorder) → 16 kHz mono WAV via FFmpegFrameGrabber + aformat filter · 3 Silero VAD runs + WeSpeaker embedding (dim 256) · 4 javax.sound SourceDataLine plays from an offset · 5 packageDmg works unsigned locally · 6 CoreML EP accepted with no fallback warning (best-effort "yes").

## Pitfalls / decisions for the ADR
- Compose `checkRuntime` refuses jpackage on Homebrew OpenJDK 21 (compose-multiplatform#3107): production packaging needs a jpackage-safe JDK (JBR or Corretto) — or `compose.desktop.packaging.checkJdkVendor=false`.
- Signing/notarization not done: Developer ID cert + codesign (nativeDistributions.macOS.signing) + notarytool needed to distribute beyond this machine.
- VAD correctness not evidenced by the sine test (0 segments expected) — the R1 experiment covers real speech.
- Model cache paths (ADR 0008 c) unchanged: macOS ~/Library/Application Support/snastro/modelli/, Windows %LOCALAPPDATA%\snastro\modelli, Linux $XDG_DATA_HOME/snastro/modelli.
