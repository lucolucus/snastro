---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0026 §1 (module, package, confinement), §2 (task names/home, cache, shim name, loading), §7 (Windows/Linux no longer OPEN: in scope); ADR 0004 (System.load admits ./llama-jni/ instead of ./llm/; check + fixtures); ADR 0006 (deny-list `llm` → `llama-jni`); ADR 0021 §1/§2 (the `:llm` row and its edges); ADR 0025 §5 pointer; architecture.md (module map, edges); code-rules.md CR-3; infra-notes (Local LLM); profile toolchain
decided: 2026-09-26 · user (JNI stays; the binding is an external, separately shareable library with zero snastro dependencies; developed fully, Windows included) · architect (name, API outline, per-OS toolchain and GPU defaults, test layout — the items marked [architect] are open to the user's veto, see "Open questions")
enforced_by:
  - check: architettura-test/controlli-adr/adr-0027-libreria-llama-indipendente.sh
    from: modello-linguistico-llama   # the block that creates ./llama-jni/; build-manifest repoints this to the library block when it splits the block
  # the System.load admission of ./llama-jni/ is ADR 0004's own check (amended again 2026-09-26 (b), cited there)
---
# 0027 — The llama.cpp binding is a separate library, `:llama-jni`, with no snastro dependency

## Context
[ADR 0026](0026-runtime-llm-jni-llama.md) chose llama.cpp b11195 in-process through our own JNI shim, in a
technical module `:llm` (package `snastro.llm`, edges `:kernel`, `:modelli`) that also knew snastro's model
cache and system properties. ADR 0026 §7 left Windows and Linux OPEN.

On 2026-09-26 the user refined the decision **[user]**:
1. Keep JNI.
2. Build the binding as an **external, separate library** that could be shared on its own.
3. Develop it **fully**: macOS arm64, Windows x64 and, unless there is a reason not to, Linux x64. This replaces
   an earlier "minimum now" instruction.

Nothing is built yet: block `modello-linguistico-llama` is in `todo`, and `./llm/` does not exist. So this changes
documents and checks only, not code.

## Decision

### 1. The library: `:llama-jni` [user; name and namespace: architect]
- **Gradle module `:llama-jni`, directory `./llama-jni/`** (top level). It replaces `:llm`, which is never created.
  It stays a normal subproject of this build for now, so `./gradlew check` covers it. It is laid out so that
  `llama-jni/` can later become an included build (`includeBuild`), its own repository, or a published artifact,
  **without edits inside it**. The extraction itself is not done now.
- **Package `io.github.lucolucus.llamajni`** (the Maven Central convention for the GitHub account that hosts
  `lucolucus/snastro`). Future coordinates: `io.github.lucolucus:llama-jni`, own version starting `0.1.0`,
  independent of snastro.
- **Zero dependencies on snastro [user], mechanically checked (§6):**
  - no project dependency;
  - no `snastro.*` package, import, type or system property;
  - no snastro build plugin (`snastro.*` convention plugins), no `rootProject` or `rootDir` access, no `../`
    path out of its directory;
  - its own build script applies public plugins only (Kotlin JVM, detekt) and configures itself (explicit API
    mode, warnings as errors, JUnit 5, its own detekt config inside `llama-jni/`).
  - Its only runtime dependency is the Kotlin stdlib. `Esito`, `ErroreDominio` and every snastro term stay out.
- **What it owns** (all inside `llama-jni/`):
  - the C shim;
  - the vendored b11195 llama.cpp/ggml headers (MIT notice kept);
  - the Windows `.def` import definitions (§4);
  - the `external` declarations and every native load;
  - the Kotlin API (§2), its tests (§5) and the native build tasks (§3);
  - `README.md` (purpose, API, build, per-OS requirements);
  - `THIRD-PARTY-NOTICES` (llama.cpp / ggml MIT, "Copyright (c) 2023-2026 The ggml authors");
  - its own `LICENSE` once the user chooses the licence (Q-2).
- **Direction:** snastro depends on the library, never the reverse. Edges: `:llama-jni` → *(none)*;
  `:sintesi:adattatori` → `:llama-jni`; `:avvio` (composition root: every module) reaches it for packaging.
  **The `:llm` row and its edges `:llm → :kernel, :modelli` are withdrawn** (architecture.md, ADR 0021 §2).

### 2. Public API (outline; the library block pins the exact signatures) [architect]
The API is English and neutral, because the library does not speak snastro's ubiquitous language. It is blocking
and thread-confined: one generation at a time per model, and the caller's thread runs it.
- `LlamaJni.load(nativeDir: Path): LlamaResult<LlamaBackend>`
  - Loads the natives from an explicit directory. It reads no system property and has no default location.
  - Idempotent per JVM.
  - Initializes the llama backend. On the dynamic-backend platforms (§4) it calls
    `ggml_backend_load_all_from_path(nativeDir)`.
- `LlamaBackend`
  - `devices: List<BackendDevice>`: name, kind CPU/GPU, free and total memory. The caller uses it to choose GPU
    or CPU.
  - `openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel>`.
- `ModelParams(nGpuLayers: Int /* ALL | 0..n */, nCtx: Int, nUbatch: Int, prefillChunk: Int = 512, flashAttention: Auto|On|Off)`
  - Validated in Kotlin (positive values, `prefillChunk ≤ nUbatch`) before any native call.
- `LlamaModel : AutoCloseable`
  - `countTokens(text: String): LlamaResult<Int>`: exact, `llama_tokenize` with special-token parsing.
  - `generate(prompt: String, options: GenerateOptions, cancel: () -> Boolean): LlamaResult<Generation>`.
  - `close()`: idempotent. It frees context and model and, when it is the last open model, calls
    `llama_backend_free`.
- `GenerateOptions(maxTokens: Int, grammar: String? /* GBNF */, grammarRoot: String = "root", lazyGrammar: Boolean = true, sampling: Sampling)`.
- `Generation(text: String, promptTokens: Int, generatedTokens: Int, stop: StopReason /* END_OF_GENERATION | MAX_TOKENS */, timings: Timings)`.
- `LlamaResult<T>` = `Ok(value)` | `Err(error: LlamaError)`, where `LlamaError` is a sealed hierarchy **that is
  not a `Throwable`** (CR-8 holds project-wide):
  - `NativeLoadFailed(detail)`, `ModelLoadFailed(detail)`, `ContextCreateFailed(detail)`;
  - `ContextOverflow(promptTokens, maxTokens, nCtx)`;
  - `GrammarInvalid(detail)`;
  - `Cancelled`;
  - `DecodeFailed(code)`;
  - `Closed`.
  Misuse, such as calling on a closed model from another thread, is a programming error (`check`), not a result.
- **Behaviour it guarantees** (the generic part of ADR 0026 §3–§5):
  1. The overflow check runs **before any decode**: `promptTokens + maxTokens > nCtx` → `ContextOverflow`, and a
     sum equal to `nCtx` proceeds.
  2. Prefill runs in `prefillChunk`-token decodes, and each one is a cancel point.
  3. A watcher polls `cancel()` and sets the native abort flag. The flag is read by `abort_callback` and between
     decode steps.
  4. `Err(Cancelled)` is returned only after the native call has returned.
  5. `maxTokens` is enforced natively and reported as `StopReason.MAX_TOKENS`.
  6. Grammar sampling is lazy (ADR 0026 §5).
- **Text crosses JNI as standard UTF-8 byte arrays, never `GetStringUTFChars`.**
  - Reason: JNI's modified UTF-8 corrupts supplementary characters (emoji) and NUL.
  - Generated bytes are accumulated natively and decoded once at the end, because a token can split a multi-byte
    character.
- **Internal seam:** every `external fun` sits behind an internal `NativeBridge` interface. The Kotlin logic
  (validation, overflow check, cancel watcher, result mapping, `close` idempotency, per-OS load plan) is then
  unit-tested with a fake bridge, on any OS, inside the gate.
- **Not in the library** (it belongs to the snastro adapter, §7): chat templates or prompt text, the answer
  schema and its GBNF, JSON parsing, the GPU→CPU retry policy, where the model and the natives live.

### 3. Build tasks, natives, cache [architect; refines ADR 0026 §2]
- **The tasks move into the library's own build script, with neutral names.**
  - `downloadLlamaNatives` (was `scaricaNativiLlama`): fetches the host's pinned b11195 asset, verifies its
    SHA-256, extracts only the libraries listed per OS (§4), and deletes the file on a mismatch.
  - `compileJniShim` (was `compilaShimLlama`): compiles the shim with the host's toolchain (§4).
  - `assembleNatives`: puts both into `llama-jni/build/natives/<os-arch>/`, the library's **one native output**.
- The llama.cpp release (`b11195`), the per-OS asset names, URLs and hashes are pinned **once, in
  `llama-jni/`**, in its build script or its own properties file. They are not in the root version catalogue:
  ADR 0026 §2's `llama-cpp` catalogue entry is withdrawn before it is created.
- **Download cache:** `<Gradle user home>/caches/llama-jni/b11195/`, outside the repo. Every worktree and a future
  standalone repo share it with no path back into snastro. This replaces `native-cache/llama-b11195/`.
- **Shim library name:** `libllamajni.dylib` / `llamajni.dll` / `libllamajni.so`. This replaces
  `libsnastro-llama-jni`. JNI symbols are `Java_io_github_lucolucus_llamajni_…`.
- **snastro side:** `:avvio` copies `assembleNatives`' output into its `appResourcesRootDir/<os-arch>/` (ADR 0016
  §3), for `:avvio:run`, `createDistributable`, `prepareAppResources`, `modelliTest` and `benchmarkRiassunto`.
- **Unchanged from ADR 0026:**
  - none of these tasks is a dependency of `check`: the gate compiles the library's Kotlin and runs its unit
    tests, with no native, no C compiler and no download;
  - nothing native or archived is tracked (ADR 0016's and ADR 0026's checks).

### 4. Platforms: developed fully [user]; toolchains and GPU defaults [architect]
| | macOS arm64 | Windows x64 | Linux x64 |
|---|---|---|---|
| Asset (b11195, ADR 0026 §2 table) | `…-bin-macos-arm64` (**verified**) | `…-bin-win-vulkan-x64` (GitHub digest `aa509079…`) | `…-bin-ubuntu-vulkan-x64` (GitHub digest `e24e11b3…`) |
| GPU default | Metal, all layers | **Vulkan**, all layers when a GPU device is reported | **Vulkan**, same |
| CPU fallback | — (Metal always present on Apple silicon) | the `ggml-cpu-*` variants shipped in the same asset | same |
| Backends | linked directly (no dynamic loading) | `GGML_BACKEND_DL`: `ggml_backend_load_all_from_path(nativeDir)` | same |
| Shim toolchain | `clang -O2 -shared`, rpath `@loader_path` | **MSVC Build Tools 2022** (`cl` + `lib`); import libraries generated from vendored `.def` files | `gcc -O2 -shared -fPIC`, rpath `$ORIGIN` |
| Native load order | shim only (rpath resolves the rest) | explicit `System.load` of `ggml-base` → `ggml` → `llama` → shim, by full path | shim only (rpath) |
| Build host | the Mac | a Windows x64 host (Q-1) | an Ubuntu 22.04 x64 host (Q-1) |

Why each choice:
- **Vulkan, not CUDA, on Windows/Linux.** It is vendor-neutral (NVIDIA, AMD, Intel, through the GPU driver's
  Vulkan loader). There is no CUDA runtime to ship: cudart is hundreds of MB, under NVIDIA's redistribution terms.
  The Vulkan asset is 33 / 31 MB zipped against 19 / 17 MB for CPU-only. It also carries the CPU backends, so one
  asset per OS covers GPU and CPU. With no Vulkan driver the Vulkan backend fails to register, and the CPU
  backends run.
- **The GPU→CPU policy is the adapter's (§7), not the library's.**
  - If the library reports a GPU device, the adapter requests all layers on it.
  - If opening the model or context on the GPU fails (e.g. too little VRAM for the ≈ 5.7 GB weights plus the
    40k context), the adapter retries once on the CPU (`nGpuLayers = 0`) in the same run.
  - Partial offload and VRAM heuristics are not built: OPEN until a real Windows GPU run measures them.
- **MSVC on Windows.** The release DLLs are MSVC-built, so MSVC keeps one CRT family and is llama.cpp's own
  Windows toolchain. MinGW would mix CRTs across the DLL boundary. clang-cl still needs the MSVC SDK and libraries
  and gains nothing. Cross-compiling from the Mac is rejected for two reasons: the Windows SDK is not licensed for
  that, and jpackage cannot cross-build a Windows installer anyway, so a Windows host is needed regardless.
  - The release zips are expected to ship DLLs without import libraries (to verify on the first fetch). So the
    shim links against import libraries that `lib /def:` generates from vendored `.def` files. The files list
    exactly the `llama_*` / `ggml_*` symbols the shim calls. If the zip does ship `.lib` files, they are used and
    the `.def` files are dropped.
- **Explicit load order on Windows.** Windows does not search the loading DLL's own directory for its
  dependencies. Preloading them by full path makes the shim's imports resolve to modules that are already loaded.
  All these loads are inside the library (ADR 0004's check admits `./llama-jni/`).
- **The shim is portable C11.** Its atomic flag goes through a ~10-line header: `<stdatomic.h>` on clang/gcc,
  `Interlocked*` on MSVC, whose C11 atomics are experimental. No C++ runtime is added.
- **Linux is treated like Windows. There is no reason to exclude it.** It uses the same dynamic-backend mechanism
  and the same Vulkan/CPU asset shape, and it is easier to verify (CI runners, containers). Stated limit: the
  release is built on Ubuntu 22.04, so the **glibc floor is 2.35**. Older distributions are unsupported.
- **macOS x64** stays documented, not wired (ADR 0026 §2 digest). Intel Macs are not a target, and the rest of
  the app (sherpa, ADR 0016) does not wire them either.
- **Wiring an OS** means all of the following, recorded in the library's README and as a dated line under this
  ADR:
  - its asset row verified: archive extension and member list read, SHA-256 matching the pinned digest;
  - the extracted library list pinned;
  - the shim built on that OS;
  - the library's native tests (§5) green on that OS.
- **Honest scope limit.** The *library* becomes fully capable on Windows and Linux. *snastro* on Windows or Linux
  remains blocked by ADR 0016 §5 (sherpa natives documented, not wired), and by installer and signing decisions
  nobody has taken (Authenticode, Linux package format: never defaulted). No `Riassunto` NFR is pinned on
  Windows or Linux: the ≤ 600 s AC of ADR 0026 §6 is the M3 Pro's. Runs there are measured and recorded, not
  gated.

### 5. Tests [architect]
- **Gate (every OS, no native, no model):** the library's unit tests over the fake `NativeBridge`:
  - parameter validation;
  - overflow check at `nCtx − 1`, `nCtx` and `nCtx + 1`;
  - the cancel watcher sets the abort flag, and `Cancelled` is returned only after the bridge call returns;
  - result and stop-reason mapping;
  - `close` idempotent, and the backend freed with the last model;
  - the per-OS load plan: Windows order, dynamic backends on or off;
  - the UTF-8 byte round trip, including a supplementary character and a multi-byte character split across two
    token pieces.
- **Opt-in native tests** (`./gradlew :llama-jni:nativeTest`, tag `native`, outside the gate):
  - Run on each OS's own host against the real natives and a **tiny public GGUF pinned by URL + SHA-256** in the
    library. Candidate: ggml-org's `stories260K.gguf`, the one llama.cpp's own CI uses; the block pins it and its
    licence. It is downloaded to the cache and never tracked (ADR 0026's check).
  - They cover:
    - 20 open/close cycles with stable RSS;
    - `countTokens` deterministic;
    - output accepted by a small GBNF;
    - `MAX_TOKENS` stop;
    - `ContextOverflow` with no decode;
    - `Cancelled` before the first decode and mid-generation, within the bound;
    - the device list;
    - on Windows, a model path with non-ASCII characters (e.g. `C:\Users\Lucà\…`).
  - They are independent of snastro. The Qwen-based contract and the NFR stay snastro's (§7).
- **snastro's opt-in tests are unchanged** (ADR 0026): `modelliTest` (AC-S152, Qwen, cancel ≤ 10 s) and
  `benchmarkRiassunto` (AC-S153), on the Mac.
- **What stays unverifiable on the dev Mac:** anything Windows or Linux native, and Vulkan on real GPUs.
  - CI runners (Q-1) prove the build, the CPU path and the native tests.
  - A **real Windows GPU run** (Vulkan, Qwen 9B) needs a physical Windows PC with a GPU. It is recorded manually
    as evidence and is not an AC of any gated block.

### 6. Enforcement
- **New check `adr-0027-libreria-llama-indipendente.sh`** (prohibition + target presence; applies `from` the
  block that creates `./llama-jni/`). It fails if:
  1. `llama-jni/build.gradle.kts` or `llama-jni/src/main/kotlin` is missing (a scan over nothing is not green);
  2. any `*.gradle.kts` under `llama-jni/` uses `project(`, the `projects.` accessor, `rootProject`, `rootDir`,
     a `snastro` plugin id or a `../` path;
  3. any `*.kt`/`*.java` under `llama-jni/` names `snastro.` in code: package, import, qualified reference, or a
     string such as a `snastro.*` system property. Comment lines never count;
  4. any `*.c`/`*.h`/`*.cpp` under `llama-jni/src` defines a `Java_snastro_` JNI symbol.
  - It prints `ADR-0027 libreria-llama-indipendente: PASS|FAIL` and runs in `ControlliAdrTest` with conforming and
    violating fixtures.
- **ADR 0004's check** admits native loading only in `ml-sherpa` and **`./llama-jni/`**. `./llm/` is no longer
  admitted, and a `llama-jni` directory nested elsewhere is not admitted either.
- **ADR 0006's check:** its deny-list names `llama-jni` instead of `llm`.
- **Mechanical, already in place:**
  - CR-3's Konsist rule forbids `java.net.` outside `snastro.modelli`, so the library can open no socket
    (ADR 0008 unchanged);
  - CR-8 forbids a `Throwable` error type;
  - `verificaDipendenzeModuli` enforces the edges.
- **Discursive (code review):**
  - the public API names no snastro concept;
  - README and notices are present;
  - UTF-8 byte crossing;
  - lazy grammar;
  - 512-token prefill chunks as the default.

### 7. The snastro adapter stays in snastro [user]
- `:sintesi:adattatori ..ml` implements `ModelloLinguistico` over the library's interfaces:
  - ChatML prompt with the empty `<think></think>`, the `Riassunto` speaker legend and `{V<n>}` translation, the
    `Argomento` instruction;
  - answer schema v1 and its bounded GBNF, and `max_tokens = ⌈3.5 × lunghezzaMassimaParole⌉ + 512`;
  - JSON parsing to `RispostaModello`;
  - `n_ctx` 40 960, `n_ubatch` 2048, prefill chunk 512;
  - load → generate → `close` on every run, with no keep-warm (ADR 0026 §3);
  - the GPU→CPU retry (§4).
- **Error mapping** to `ErroreApplicazioneSintesi`:

  | Library error | snastro error |
  |---|---|
  | `ContextOverflow(p, m, n)` | `IngressoTroppoLungo(p)` |
  | `Cancelled` | `Annullato` |
  | `StopReason.MAX_TOKENS`, schema mismatch | `RispostaNonValida` |
  | other `LlamaError`s | `ErroreRuntime(motivo)` |
  | model file missing | `ModelloNonDisponibile` |

- **Locations are composition configuration, resolved by `:avvio` and handed to the adapter as suppliers, read
  lazily at the first run.**
  - The native directory: `snastro.llm.native.path` if set, else `compose.application.resources.dir`. If neither
    is set, the result is `ErroreRuntime` naming both properties.
  - The installed GGUF path comes from `:modelli`. `:sintesi:*` still has no edge to `:modelli` (ADR 0021).
- The adapter unit-tests against fakes of the library's interfaces, inside the gate.

## Consequences
- **Build-manifest:** block `modello-linguistico-llama` is split into a library block, per-OS library blocks and
  the snastro adapter block (the architect's hand-off lists the ACs). This ADR's check `from` is repointed to the
  library block.
- **Amended in place with dated pointers here:** ADR 0026 (§1, §2, §7), ADR 0004 (check + fixtures), ADR 0006
  (check + fixture), ADR 0021 (§1/§2 row), ADR 0025 (§5 pointer), architecture.md, code-rules.md CR-3,
  infra-notes, profile toolchain.
- **Accepted costs:**
  - a second public API to keep stable (additive by default once published);
  - three native toolchains;
  - a Windows and a Linux build host (Q-1);
  - the library's own detekt config and README to maintain.
- **Gained:**
  - the binding can be extracted with `git subtree split --prefix=llama-jni` or as an included build, with no
    edits inside it;
  - snastro's domain stays free of JNI;
  - the gate proves the binding's Kotlin logic on every OS.

## Open questions for the user (the orchestrator asks; none blocks the library's macOS block)
- **Q-1 · Build and verification host for Windows/Linux** (infra, never defaulted).
  - Recommended: a GitHub Actions matrix on `lucolucus/snastro` with `macos-14`, `windows-2022` and
    `ubuntu-22.04`. Each job runs `:llama-jni:check`, `compileJniShim` and `:llama-jni:nativeTest` (CPU).
  - Cost: on a private repo, Windows minutes bill ×2 and macOS ×10.
  - Alternative: a physical Windows PC, which is also the only way to run the Vulkan GPU check.
  - The Windows and Linux library blocks are `ready_when` Q-1 is answered.
  - The Windows jobs run the library's gate, not snastro's whole gate. snastro's ADR checks need a POSIX `sh`,
    and snastro-on-Windows is ADR 0016 §5's open item.
- **Q-2 · The library's own licence** (recommended MIT, matching llama.cpp). The llama.cpp/ggml MIT notice ships
  regardless.
- **Q-3 · Name and namespace:** `llama-jni` / `io.github.lucolucus.llamajni`. Renaming is free until the first
  publication.
- **Q-4 · Windows GPU default** (Vulkan, CPU fallback) and the absence of a Windows/Linux `Riassunto` NFR.
  Confirm, or name a target machine and budget.
