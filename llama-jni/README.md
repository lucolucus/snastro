# llama-jni

A small, blocking JNI binding of [llama.cpp](https://github.com/ggml-org/llama.cpp) for the JVM:
load a GGUF model, count tokens, generate text (optionally constrained by a GBNF grammar), cancel.
Package `io.github.lucolucus.llamajni`, licence MIT (see `LICENSE`, `THIRD-PARTY-NOTICES`).

- llama.cpp release **b11195**, used exactly as released (never patched or rebuilt): the libraries come
  from the release asset; only the thin C11 shim `libllamajni` is compiled here.
- Runtime dependency: the Kotlin standard library only.
- The library chooses no location: the caller passes the native directory and the model path.

## Public API

```kotlin
object LlamaJni { fun load(nativeDir: Path): LlamaResult<LlamaBackend> }   // idempotent per JVM

interface LlamaBackend {
    val devices: List<BackendDevice>                                         // name, CPU/GPU, free/total memory
    fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel>
}
data class ModelParams(nGpuLayers: Int /* ModelParams.ALL | 0..n */, nCtx: Int, nUbatch: Int,
                       prefillChunk: Int = 512, flashAttention: FlashAttention = FlashAttention.AUTO)

interface LlamaModel : AutoCloseable {
    fun countTokens(text: String): LlamaResult<Int>
    fun generate(prompt: String, options: GenerateOptions, cancel: () -> Boolean): LlamaResult<Generation>
    override fun close()
}
data class GenerateOptions(maxTokens: Int, grammar: String?, grammarRoot: String = "root",
                           lazyGrammar: Boolean = true, sampling: Sampling = Sampling())
data class Sampling(temperature: Float = 0.8f, topK: Int = 40, topP: Float = 0.95f, seed: Int = Sampling.RANDOM_SEED)
data class Generation(text: String, promptTokens: Int, generatedTokens: Int, stop: StopReason, timings: Timings)
enum class StopReason { END_OF_GENERATION, MAX_TOKENS }
data class Timings(loadMs: Long, prefillMs: Long, generationMs: Long)

sealed interface LlamaResult<T> { Ok(value); Err(error: LlamaError) }
sealed interface LlamaError {   // NOT a Throwable
    NativeLoadFailed(detail); ModelLoadFailed(detail); ContextCreateFailed(detail)
    ContextOverflow(promptTokens, maxTokens, nCtx); GrammarInvalid(detail); Cancelled; DecodeFailed(code); Closed
}
```

`Sampling` (pinned here, additive from now on): top-k (`0` = off) → top-p (`1` = off) → temperature
(`0` = greedy) → a seeded draw (`RANDOM_SEED` = -1 = a new seed per generation). `Timings` are wall-clock
milliseconds: `loadMs` is the model + context open, `prefillMs` the prompt decode, `generationMs` the rest.

`LlamaJni.load` is idempotent per JVM: once it has succeeded, a LATER call — even with a DIFFERENT
`nativeDir` — loads nothing and silently returns the SAME backend the first call returned.

Example:

```kotlin
val backend = (LlamaJni.load(nativeDir) as LlamaResult.Ok).value
(backend.openModel(gguf, ModelParams(nGpuLayers = ModelParams.ALL, nCtx = 8192, nUbatch = 512)) as LlamaResult.Ok)
    .value.use { model ->
        val result = model.generate("Once upon a time", GenerateOptions(maxTokens = 64, grammar = null)) { false }
    }
```

### Behaviour it guarantees

1. **Overflow before any decode:** `promptTokens + maxTokens > nCtx` → `Err(ContextOverflow)`; a sum equal
   to `nCtx` proceeds. (`nCtx` is the context size llama.cpp actually allocated.)
2. **Prefill in `prefillChunk`-token decodes**, each a cancel point.
3. **Cancellation:** `cancel()` is checked before the first decode and before every prefill chunk, and a
   watcher thread polls it (every 10 ms) and raises the native abort flag, read by llama.cpp's
   `abort_callback` and between generated tokens. `cancel` must therefore be thread-safe.
   `Err(Cancelled)` is returned only after the native call has returned.
4. **`maxTokens` is enforced natively** and reported as `StopReason.MAX_TOKENS`.
5. **Lazy grammar** (default): sample without the grammar, check only the chosen token, and apply the
   grammar to the whole vocabulary (then resample) only when it rejects it. `lazyGrammar = false` applies it
   to every step.
6. **UTF-8 bytes across JNI** (prompt, grammar, path, output): never JNI's modified UTF-8. The output bytes
   are accumulated natively and decoded once, because a token can end inside a multi-byte character.
7. **Memory:** `close()` frees the context, then the model; the last open model also calls
   `llama_backend_free` (the next `openModel` initializes the backend again). `close()` is idempotent;
   `countTokens` / `generate` after it return `Err(Closed)`.
8. **Threads and misuse — always a programming error (an unchecked exception), never a `LlamaError`:**
   - a call from another thread than the one that opened the model (`IllegalStateException`);
   - an invalid `ModelParams` / `GenerateOptions` / `Sampling` (`IllegalArgumentException`);
   - `generate` with a `prompt` that tokenizes to zero tokens (`IllegalArgumentException`);
   - a native `tokenize` failure — out-of-memory or an `int32` overflow, vanishingly rare in practice
     (`IllegalStateException`).

The prompt is used as given: no chat template, no BOS/EOS added (`llama_tokenize` with special-token
parsing, `add_special = false`); `countTokens` counts the same way.

## Build

| Task | What it does |
|---|---|
| `test` (part of `check`) | unit tests over a fake native bridge: no native library, no model, no C compiler, no download |
| `downloadLlamaNatives` | fetches the host's pinned b11195 asset into `<Gradle user home>/caches/llama-jni/b11195/`, verifies its SHA-256 (a mismatch deletes the file and fails), extracts only the pinned libraries |
| `compileJniShim` | compiles `src/main/c/llamajni.c` into `libllamajni` against the vendored headers (`src/main/c/include/`, b11195) and the JDK's JNI headers |
| `assembleNatives` | writes the shim + the llama.cpp libraries to `build/natives/<os-arch>/`, the library's one native output — the directory to pass to `LlamaJni.load` |
| `nativeTest` | opt-in: the tests tagged `native` against the real natives and the pinned `stories260K.gguf` (cached, never versioned); prints `llama-jni nativeTest: PASS` |

None of the native tasks is a dependency of `check`. Pins (release, asset URL + SHA-256, extracted
library list, test model URL + SHA-256 + licence) live once, in `build.gradle.kts`. A release bump = new
tag + new hashes + re-vendored headers + the shim re-checked against the new `llama_*_params` ABI.

## Platforms

| | macOS arm64 | Windows x64 | Linux x64 |
|---|---|---|---|
| Status | **wired and tested** (M3 Pro, Metal) | pending its build host (ADR 0027 Q-1) | pending its build host (ADR 0027 Q-1) |
| Asset (b11195) | `llama-b11195-bin-macos-arm64.tar.gz`, SHA-256 `5320d5f9…3672f98` | `…-bin-win-vulkan-x64` | `…-bin-ubuntu-vulkan-x64` |
| Toolchain | Xcode command-line tools (`clang`), JDK 21 | MSVC Build Tools 2022 | gcc |
| GPU | Metal, backends linked directly | Vulkan + CPU fallback, dynamic backends | Vulkan + CPU fallback, dynamic backends |
| Load plan | `libllamajni.dylib` only; its rpath `@loader_path` resolves `libllama` / `libggml*` | not wired | not wired |

On any host other than macOS arm64 the three native tasks fail with a message naming this.
Libraries extracted on macOS arm64: `libllama.0.dylib`, `libggml.0.dylib`, `libggml-base.0.dylib`,
`libggml-cpu.0.dylib`, `libggml-blas.0.dylib`, `libggml-metal.0.dylib`, `libggml-rpc.0.dylib`.
If you sign the app, every one of them and `libllamajni.dylib` must be signed.
