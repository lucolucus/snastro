---
scope: global
status: accepted
supersedes: null
closes_spike: packaging-modelli-desktop
enforced_by: "! git ls-files | grep -qE '(\\.(dylib|so|dll|jnilib)|sherpa-onnx[^/]*\\.(jar|tar\\.bz2))$'"
---
# 0016 — sherpa-onnx natives: pinned GitHub release assets, a SHA-verified Gradle fetch, Compose app resources, explicit load

## Context
Spike `packaging-modelli-desktop` asked whether a Compose Desktop app can load the sherpa-onnx JNI
and onnxruntime natives, decode an `.m4a` with bytedeco FFmpeg, run VAD and one speaker embedding,
and play audio from an offset. It had to do this both from `./gradlew run` and from a jpackage
`.dmg`. The evidence is in `features/trascrizione-con-parlanti/research/spike-packaging-modelli-desktop.md`.
The spike ran on 2026-09-24 on macOS arm64 (M3 Pro). It used a scratch Kotlin/JVM 21 hello-world
and synthetic audio. The repo was not touched.

**Result.** All six steps passed in three launch modes: `./gradlew run`, the `.app` binary, and the
binary from the mounted `.dmg`. The steps were:
1. the natives load;
2. an m4a decodes to 16 kHz mono WAV (javacv `FFmpegFrameGrabber` with an `aformat` filter);
3. Silero VAD runs, and a WeSpeaker embedding (dim 256) is extracted;
4. `javax.sound` `SourceDataLine` plays from an offset;
5. `packageDmg` works unsigned on this machine;
6. the CoreML execution provider is accepted with no fallback warning.

**Limits of the evidence.**
- VAD *correctness* is not shown. The sine test expects 0 segments. The R1 experiment on real
  speech covers this.
- CoreML is "accepted", not measured, and not proven to run the graph.
- Windows and Linux are documented from the release asset names only. They were not run.
- Signing and notarization were not done.

## Decision

### 1. Coordinates: GitHub release assets, pinned (NOT Maven Central)
sherpa-onnx has **no Maven Central artifact**. It is consumed from the k2-fsa GitHub release
**v1.13.8**:

| Asset | URL | SHA-256 | Used |
|---|---|---|---|
| `sherpa-onnx-jvm-1.13.8.jar` (Java API, `com.k2fsa.sherpa.onnx.*`) | https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar | `77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b` | compile + runtime classpath of `:ml-sherpa` |
| `sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2` | https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2 | `2505fd9bd46a28615ab3a14833caae8479c80993658402d57a949d7403c931ad` | only its members `lib/libonnxruntime.dylib` and `lib/libsherpa-onnx-jni.dylib` |

- `otool -L` shows that `libsherpa-onnx-jni.dylib` needs only `libonnxruntime.dylib` and system
  libraries. No other member of the tarball is needed.
- **Not used:** the alternative `sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar`. It carries
  byte-identical dylibs for a classpath loader. We use one layout, not two.
- The version is set once, as `sherpa-onnx = "1.13.8"` in `gradle/libs.versions.toml`. The URLs
  and SHA-256 values are set once, in the build script that owns the fetch tasks. A version bump is
  a single edit plus new hashes, recorded as an amendment to this ADR.

### 2. The fetch: two Gradle tasks, SHA-256 verified, the gate stays native-free
The download cache is `native-cache/sherpa-onnx-1.13.8/` at the repo root. It is already
gitignored and survives `clean`.
- **`scaricaJarSherpa`** downloads the JVM jar and verifies its SHA-256. On a mismatch the task
  fails, deletes the file, and nothing reaches the classpath.
  - `:ml-sherpa` depends on the jar through `files(...)` with `builtBy(scaricaJarSherpa)`.
  - So the **gate** (`./gradlew check`) downloads this pure-Java jar once, then works offline.
  - Loading the jar's classes loads no native code: there is no static initializer (spike). The
    gate still runs **no native library**, as the profile promises.
- **`scaricaNativiSherpa`** replaces the wave-0 stub of the same name in the root
  `build.gradle.kts`. The spike called it `fetchSherpaOnnxNatives`, but the stub's name wins.
  - It downloads the host's `*-jni.tar.bz2` and verifies its SHA-256 (mismatch: fail, delete).
  - It extracts **exactly the two libs**, flat, into `<appResourcesRootDir>/macos-arm64/`.
  - Its inputs and outputs are declared, so a second run is up to date and makes no network call.
  - **It is a dependency of `:avvio:run`, `:avvio:createDistributable`,
    `:avvio:prepareAppResources` and `modelliTest`.** Compose's Gradle validation fails if
    `prepareAppResources` reads the directory without this edge.
  - It is **never** a dependency of `check`.
  - On a host os-arch with no pinned asset, the task fails with a message naming the asset (see §5).

### 3. Resource layout (Compose `nativeDistributions.appResourcesRootDir`)
- `appResourcesRootDir` belongs to `:avvio`. It is a **generated directory under `avvio/build/`**
  (for example `risorse-app/`), never under `src/`, so natives can never be committed.
  - `macos-arm64/`: `libonnxruntime.dylib` and `libsherpa-onnx-jni.dylib`. Later siblings are
    `macos-x64/`, `windows-x64/` and `linux-x64/`.
  - `common/`: shared resources. None are needed today.
- At runtime `System.getProperty("compose.application.resources.dir")` is the **flattened merge**
  of `common/` and the current os-arch folder, with the `common/` prefix removed. So the two libs
  sit **directly** in that directory.
  - In the `.app` the directory is `Contents/app/resources/`.
  - Under `./gradlew :avvio:run` it is the prepared resources directory. The spike checked both.
- The bytedeco FFmpeg natives (ADR 0005) are **not** part of this layout. They travel in their
  classifier jars on the classpath, and the spike needed no extra layout for them in any launch mode.

### 4. Loading: explicit, lazy, confined to `:ml-sherpa`
- sherpa-onnx has **no static initializer**. The natives must be loaded **explicitly**:
  - set the system property `sherpa_onnx.native.path` to a flat directory holding both libs;
  - then call `com.k2fsa.sherpa.onnx.LibraryUtils.load()`. It `System.load`s onnxruntime first,
    then the JNI lib.
- **`java.library.path` is not used.** `LibraryUtils.load()` is idempotent.
- This is `MotoreSherpa.caricaNativi()` (boundary `tec-ml-sherpa`). It resolves the directory in
  this order:
  1. `sherpa_onnx.native.path` if already set: the `modelliTest` JVM sets it to
     `<appResourcesRootDir>/macos-arm64/`;
  2. otherwise `compose.application.resources.dir`;
  3. neither set, or the libs are missing → a clear failure that names both properties. It is never
     a JVM `UnsatisfiedLinkError` with no context.
- `conSessione` calls `caricaNativi()` lazily, before the first sherpa object exists. Consumers and
  `:avvio` never call `LibraryUtils` or `System.load`. ADR 0004's `enforced_by` already confines
  both to `:ml-sherpa`.
- The execution provider is **per-instance configuration** (`ConfigSessione.provider`). It is not
  a load-time choice.

### 5. Windows / Linux: documented, NOT tested, not wired in v1
| os-arch folder | Asset (v1.13.8) | Libs to extract | Status |
|---|---|---|---|
| `windows-x64/` | `sherpa-onnx-v1.13.8-win-x64-jni.tar.bz2` | `onnxruntime.dll`, `sherpa-onnx-jni.dll`, probably under `bin/` | **verify the tar members** and pin the SHA-256 before wiring |
| `linux-x64/` | `sherpa-onnx-v1.13.8-linux-x64-jni.tar.bz2` | `lib/libonnxruntime.so`, `lib/libsherpa-onnx-jni.so` | pin the SHA-256 before wiring |
| `macos-x64/` | not examined by the spike | — | undocumented; examine when needed |

- The model cache paths are unchanged (ADR 0008 (c)):
  - macOS: `~/Library/Application Support/snastro/modelli/`;
  - Windows: `%LOCALAPPDATA%\snastro\modelli`;
  - Linux: `$XDG_DATA_HOME/snastro/modelli`.
- Wiring another OS means adding one row to §1 with its SHA-256, recorded as an amendment.

### 6. Execution provider: CPU stays the default until measured
- The spike shows only that onnxruntime **accepts** the CoreML EP, with no fallback warning. It
  does not show that CoreML runs the graph, or that it is faster. ONNX Runtime can place part of a
  graph on CoreML and silently run the rest on CPU.
- **CPU remains the default and the only provider built in R1.**
- `ConfigSessione.provider` keeps its default `"cpu"`. **No user setting for CoreML is built.**
- CoreML becomes eligible only when `benchmark-elaborazione` has measured it against CPU. That
  means the same real 60-minute sample and per-phase times against the ADR 0011 budget. The
  manifest delta proposes this as an opt-in extension of that block.
- A change of default needs an amendment to this ADR that cites the measurement.

## OPEN — decisions for the user (NOT taken here; they do not block R1)
R1 runs from source with `./gradlew :avvio:run` (ADR 0001, infra-notes "Packaging": v1 is unsigned
and runs from source). Neither question below blocks R1. **Both block a distributable `.dmg`**,
which is the later packaging infra block. They are recorded so that block starts from a decision,
not a discovery.

**(O-1) Which JDK the `.dmg` bundles.**
- **Why it is open.** Compose `checkRuntime` refuses jpackage on Homebrew OpenJDK 21
  (compose-multiplatform#3107). The spike machine's JDK is Homebrew OpenJDK 21 (profile
  `capacity`).
- **What is unknown.** The project toolchain is JBR 21 (ADR 0001). Whether the Compose packaging
  tasks use that toolchain or the Gradle daemon's JDK is *not verified*.
- **The options:**
  - **(a) JetBrains Runtime 21.** Pin `nativeDistributions.javaHome` to the toolchain's JBR. This
    is the same runtime as development under ADR 0001, and no new vendor.
  - **(b) Amazon Corretto 21.** A jpackage-safe JDK, but a second JDK vendor in the project.
  - **(c) Keep Homebrew OpenJDK** with `compose.desktop.packaging.checkJdkVendor=false`. This
    silences the check that exists because Homebrew builds are known to produce broken packages,
    so the risk moves to runtime.
- The architect leans to (a), for consistency with ADR 0001. **The user chooses.**

**(O-2) Signing and notarization of the `.dmg`.**
- **The options:**
  - **(a) Unsigned, this Mac only.** This is what the spike proved. Gatekeeper blocks it on other
    Macs.
  - **(b) Ad-hoc signed** (`codesign -s -`). Still not distributable.
  - **(c) Developer ID signing and notarization.** This needs an Apple Developer Program account,
    `nativeDistributions.macOS.signing`, `notarytool`, stapling, and the hardened runtime with the
    entitlements a JVM needs. Every nested Mach-O must be signed: the sherpa JNI, onnxruntime, and
    the bytedeco FFmpeg dylibs *inside their jars*.
- The exact entitlement set and the in-jar signing are **not verified**. Establishing them is the
  infra block's first task if (c) is chosen.
- infra-notes already says "signing only if an Apple Developer account is used". **The user
  decides whether and when.**

## Consequences
- **The spike is closed.** The `ml-sherpa-motore` gate and the packaging half of the `vad-silero`
  and `benchmark-elaborazione` gates are satisfied. Their ACs come from the manifest delta
  `features/trascrizione-con-parlanti/manifest-deltas/2026-09-24-packaging.md`.
- **The gate needs network access once**, for the ~jar-sized `scaricaJarSherpa` download. After
  that it works offline. `modelliTest`, `:avvio:run` and packaging also download the natives once.
- **No native binary or sherpa artifact is ever tracked by git** (`enforced_by`). The rule matches
  any tracked `.dylib/.so/.dll/.jnilib`, and any `sherpa-onnx*.jar` / `*.tar.bz2`. Validated via
  `bash -c` on 2026-09-24:
  - exit 0 on the tree;
  - the regex matches the probe paths `ml-sherpa/libsherpa-onnx-jni.dylib`,
    `native-cache/sherpa-onnx-jvm-1.13.8.jar` and `x/sherpa-onnx-v1.13.8-osx-arm64-jni.tar.bz2`;
  - it does not match `*.kt`.
- **Blind spot:** a native file renamed to another extension. That case is left to code review.
- **ADR 0004 and ADR 0005 carry dated amendments** (2026-09-24) pointing here. Their decisions
  stand. The amendments answer the questions they left to the spike.
