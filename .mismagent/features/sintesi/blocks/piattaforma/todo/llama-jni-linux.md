---
id: "llama-jni-linux"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 2
release: "R4"
module: ":llama-jni (Linux x64 wiring: asset row, gcc shim build, load plan)"
consumes:
  - "tec-llama-jni"
related_adrs:
  - "0016"
  - "0026"
  - "0027"
ready_when: "user decides the Windows/Linux build host (Q-1 answered 'Mac only for now' on 2026-09-26)"
tests_nl_status: "draft"
---
# llama-jni-linux — llama-jni su Linux x64: Vulkan + CPU, shim gcc, backend dinamici (ADR 0027 §4)

## What to do
Wire :llama-jni on Linux x64 (ADR 0027 §4): verify and pin the b11195 ubuntu-vulkan-x64 asset row and its library list; build the shim with gcc (-O2 -shared -fPIC, rpath $ORIGIN); the Linux load plan (shim only, dynamic backends via ggml_backend_load_all_from_path); :llama-jni:nativeTest green on an Ubuntu 22.04 x64 host; README (glibc floor 2.35) + dated wiring line under ADR 0027. Vulkan with CPU fallback; no Riassunto budget. NOT READY until the user picks the build host.

Note: Split 2026-09-26 from modello-linguistico-llama (ADR 0027 §4). NOT READY: waits for the user's build-host decision (Q-1 answered 'Mac only for now', D-0007; recommended: GitHub Actions ubuntu-22.04 runner). Release R4 (library cross-platform), NOT part of R3. snastro on Linux stays blocked by ADR 0016 §5 and the undecided Linux package format.

**ready_when:** user decides the Windows/Linux build host (Q-1 answered 'Mac only for now' on 2026-09-26).

## Tasks
- AC-S177 The b11195 '…-bin-ubuntu-vulkan-x64' asset row is verified (archive extension and member list read, SHA-256 equal to the pinned digest e24e11b3…) and its extracted library list pinned; on an Ubuntu 22.04 x64 host downloadLlamaNatives → compileJniShim (gcc -O2 -shared -fPIC, rpath $ORIGIN) → assembleNatives produce libllamajni.so in llama-jni/build/natives/linux-x64/; the Linux load plan (inside the gate, fake NativeBridge) loads the shim only and calls ggml_backend_load_all_from_path(nativeDir) (dynamic backends ON); still no task is a dependency of check
- AC-S178 `./gradlew :llama-jni:nativeTest` is green on the Ubuntu 22.04 x64 host (CPU path at minimum; Vulkan device listed when a driver is present): the AC-S171 set; README states the glibc floor 2.35 and the Linux requirements; a dated 'Linux x64 wired' line is recorded under ADR 0027; no Riassunto time budget is asserted (Q-4)

## Dependencies
- **tec-llama-jni** (consumed; owner llama-jni-libreria; projection in-process; contract_test `consumer-driven`)
  - `LlamaJni (io.github.lucolucus.llamajni)`: object { fun load(nativeDir: Path): LlamaResult<LlamaBackend> } — explicit directory, reads NO system property, no default location; idempotent per JVM; initializes the llama backend (dynamic-backend platforms: ggml_backend_load_all_from_path(nativeDir))
  - `LlamaBackend`: interface { val devices: List<BackendDevice>; fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel> }
  - `BackendDevice`: data class(name: String, kind: DeviceKind, freeMemoryBytes: Long, totalMemoryBytes: Long)
  - `DeviceKind`: enum { CPU, GPU }
  - `ModelParams`: data class(nGpuLayers: Int /* ModelParams.ALL | 0..n */, nCtx: Int, nUbatch: Int, prefillChunk: Int = 512, flashAttention: FlashAttention = FlashAttention.AUTO) — validated in Kotlin before any native call (positive values, prefillChunk ≤ nUbatch)
  - `FlashAttention`: enum { AUTO, ON, OFF }
  - `LlamaModel`: interface : AutoCloseable { fun countTokens(text: String): LlamaResult<Int> /* exact, llama_tokenize with special-token parsing */; fun generate(prompt: String, options: GenerateOptions, cancel: () -> Boolean): LlamaResult<Generation>; override fun close() /* idempotent; frees context + model; llama_backend_free after the LAST open model */ } — blocking, thread-confined, one generation at a time per model
  - `GenerateOptions`: data class(maxTokens: Int, grammar: String? /* GBNF */, grammarRoot: String = "root", lazyGrammar: Boolean = true, sampling: Sampling = Sampling())
  - `Sampling`: data class of sampler settings (temperature, top-k/top-p, seed) — exact field set and defaults pinned by llama-jni-libreria in its README when built; additive thereafter
  - `Generation`: data class(text: String, promptTokens: Int, generatedTokens: Int, stop: StopReason, timings: Timings)
  - `StopReason`: enum { END_OF_GENERATION, MAX_TOKENS }
  - `Timings`: data class(loadMs: Long, prefillMs: Long, generationMs: Long) — exact field set pinned by llama-jni-libreria; additive thereafter
  - `LlamaResult`: sealed <T> { Ok(value: T); Err(error: LlamaError) }
  - `LlamaError`: sealed, NOT a Throwable (CR-8) { NativeLoadFailed(detail: String); ModelLoadFailed(detail: String); ContextCreateFailed(detail: String); ContextOverflow(promptTokens: Int, maxTokens: Int, nCtx: Int); GrammarInvalid(detail: String); Cancelled; DecodeFailed(code: Int); Closed } — misuse (e.g. a call from another thread) is a programming error (check), not a result
  - `NativeBridge (internal)`: internal interface behind every external fun — NOT part of the public API; the seam the library's unit tests fake
  - key `native directory / model path`: minted by the CALLER (snastro: :avvio's suppliers, ADR 0027 §7) — the library never derives a location
  - guarantees: overflow check before any decode (sum = nCtx proceeds); prefill in prefillChunk decodes, each a cancel point; Err(Cancelled) only after the native call returned; maxTokens enforced natively → MAX_TOKENS; lazy grammar; text crosses JNI as standard UTF-8 bytes (ADR 0027 §2)
- llama-jni-libreria — build dependency (merged before this block)

Sources: ADR 0027 §4–§5, decisions.md D-0007; related_adrs 0016, 0026, 0027; tactical-model: features/sintesi/tactical-model.md
