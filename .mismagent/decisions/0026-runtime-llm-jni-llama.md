---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0004 (System.load admits ./llm/), ADR 0021 §4 (input line) and §5 (LimiteIngresso, deferred items), ADR 0025 §5 (catalogue values); infra-notes (Local LLM, Riassunto NFR); context-map (spike closed, budget line)
closes_spike: runtime-llm-in-app
decided: 2026-09-26 · user (runtime, INV-S9 bound, calibrations), from spike evidence features/sintesi/spikes/runtime-llm-in-app.md
enforced_by:
  - check: architettura-test/controlli-adr/adr-0026-llm-non-versionato.sh
  # the System.load admission of ./llm/ is enforced by ADR 0004's own check (amended 2026-09-26, fixtures
  # conforme/llm, violante-k2fsa-in-llm, violante-llm-annidato, violante-runtime-load), cited there, not here
---
# 0026 — The local LLM runs in-process: llama.cpp b11195 through our own JNI shim, in `:llm`

## Context
Spike `runtime-llm-in-app` (evidence: `features/sintesi/spikes/runtime-llm-in-app.md`, branch
`spike/runtime-llm-in-app` @ 5abb693, never integrated) prototyped both options behind one interface and ran
them from `./gradlew run` and from the `.dmg` on the M3 Pro, on Via Roquel (1 h, 25 117 input tokens):
- **JNI shim** over the llama.cpp C API: 184.5 s from the `.dmg` (budget grammar), schema-valid;
- **`llama-server` sidecar** over a Unix-domain socket: 202.6 s from the `.dmg`, schema-valid.

Both are inside the closure band (175 s ± 20 %). Speed is the same (same libllama); the wall time is set by the
number of output tokens. The worker recommended the sidecar (0.05 s SIGKILL cancel in every phase, total memory
release, no C we compile). **The user chose JNI in-process (2026-09-26).** The accepted costs: a per-OS C shim we
compile, a C-struct ABI that changes between llama.cpp releases, a prefill-cancel latency bounded by the chunk
size, and a native abort can kill the app (the ADR 0004 recovery applies).

The spike also measured what the provisional constants of ADR 0021 §5 guessed:
- labelled input: **2.51 characters/token** (the provisional ÷ 3 under-estimated by ≈ 17 %: not conservative);
- the `m:ss` timestamps cost **21.3 %** of the input tokens (Qwen splits digits one token each);
- whole-JSON answer ≈ **3.0 tokens per Italian word** (2.5–3.5); 2500 words do not fit a 32 768 context at the
  maximum input;
- an **unbounded grammar ran away** once in 5 runs (`fonti` 1, 2, 3 … 771 until the context filled).

## Decision

### 1. Runtime: llama.cpp in-process via JNI [user]
- **llama.cpp release `b11195`** (2026-09-26), MIT ("Copyright (c) 2023-2026 The ggml authors"). Used as
  released: we never patch or rebuild llama.cpp.
- **Our own C shim** (≈ 150 lines, `llm/src/main/c/`) over the C API of that tag, exposing to Kotlin `external`
  functions in `snastro.llm`: `carica` (load model + context) / `contaToken` / `genera` / `annulla` / `libera`.
  The llama.cpp / ggml **headers of tag b11195** needed to compile it are vendored as text next to the shim (MIT
  notice kept); they change only with a release bump.
- **Confinement:** the shim, the `external` declarations and the one `System.load` live only in the top-level
  `:llm` module (`./llm/`). ADR 0004 is amended to admit exactly that path (its check: `com.k2fsa` stays
  `:ml-sherpa`-only, a directory named `llm` nested elsewhere is not admitted). **ADR 0008 is unchanged:** there
  is no loopback client, no socket, no HTTP in `:llm`.
- **Rejected:** the `llama-server` sidecar (user choice, see Context); `de.kherud:llama` / java-llama.cpp
  (last release 4.2.0 of 2025-06-20 predates the `qwen35` architecture; unmaintained); the Ollama blob behind the
  model-choice measurements (Ollama-specific GGUF with vision/MTP tensors; b11195 refuses it).

### 2. Natives: pinned release assets, fetched at build time, never versioned (ADR 0016 discipline)
| Asset (b11195) | SHA-256 | Status |
|---|---|---|
| `llama-b11195-bin-macos-arm64.tar.gz` (11 751 001 B) | `5320d5f90fde78fd046f78c2eab3e0cbde2ccd8b6aa4d3bcd6c15cbbb3672f98` | **verified, wired** |
| `…-bin-macos-x64` | `df5f585b4b4da45c49b85e1f6a8d8ed9b39871e8d978f0d8add9aa02d0a2b821` | GitHub digest, not run |
| `…-bin-win-cpu-x64` | `9416e8bdd133a08a749e71a98435d9b59712e39e914b037ed82f79e2fa8a5093` | GitHub digest, not run |
| `…-bin-win-vulkan-x64` | `aa509079b1bbe7bc648e17eab48291379eb11e19853c19855838b46db80f60c9` | GitHub digest, not run |
| `…-bin-ubuntu-x64` | `17bcb7c54c99447e58c9b970ae69e7da26d308b37f12a9c486908dd1ae4ed164` | GitHub digest, not run |
| `…-bin-ubuntu-vulkan-x64` | `e24e11b3597e5de86ca3c0279e04fd308e75c1a6a5159ab9dbe598c1534577b8` | GitHub digest, not run |

Base URL: `https://github.com/ggml-org/llama.cpp/releases/download/b11195/<asset>`.
- A Gradle task **`scaricaNativiLlama`** fetches the host's asset into `native-cache/llama-b11195/`, verifies
  its SHA-256 (mismatch: fail, delete), and extracts only `libllama` + the `libggml*` libraries (mac: base, cpu,
  blas, metal, rpc, ggml ≈ 10.6 MB with the shim).
- A Gradle task **`compilaShimLlama`** compiles the shim for the host (mac: `clang -O2 -shared` against the
  vendored headers and the JDK headers, `-lllama`, rpath `@loader_path`) into `libsnastro-llama-jni.<ext>`.
- Both place their output in `:avvio`'s `appResourcesRootDir/<os-arch>/` (ADR 0016 §3) and are dependencies of
  `:avvio:run`, `createDistributable`, `prepareAppResources`, `modelliTest` and `benchmarkRiassunto`, **never**
  of `check`: the gate compiles `:llm`'s Kotlin (the `external` declarations need no native) and runs no native
  code and no C compiler.
- The version is set once (`llama-cpp = "b11195"` in `gradle/libs.versions.toml`); URLs and hashes once, in the
  build script owning the tasks. A bump = new hashes + re-vendored headers + shim recompiled and re-checked
  against the new `llama_context_params`/`llama_model_params` ABI, recorded as an amendment here.
- **Never tracked by git:** release archives and GGUF weights (this ADR's check), native libraries (ADR 0016's).
- **Loading:** explicit and lazy, in `:llm` only, like ADR 0016 §4: the directory is the system property
  `snastro.llm.native.path` if set (`modelliTest`, benchmark), else `compose.application.resources.dir`; neither,
  or the libs missing → a clear failure naming both properties. One `System.load` of the shim; on macOS it
  resolves `libllama`/`libggml*` from the same directory through its rpath.

### 3. Execution parameters (macOS arm64, Metal)
- **GPU:** all layers on Metal (`n_gpu_layers` = all; measured pp2048 333 vs 68 t/s on CPU). The mac build
  links the Metal/CPU/BLAS backends directly: no dynamic backend loading.
- **`n_ubatch` 2048**, flash-attention auto (best measured at depth 24k: 216 t/s prefill, 16.9 t/s generation).
- **Context `n_ctx` = 40 960 tokens** (§5): fits the maximum input plus the answer at the 2500-word cap. KV cost
  is small (the hybrid model has 8 attention layers); peak footprint at 40k is not yet measured (1.4–1.8 GB at
  32k, plus the ≈ 5.7 GB file-backed weights mmap).
- **Prompt:** the model's ChatML written by the adapter, with an empty `<think></think>` (thinking off); it
  matches the model's jinja template (spike: identical token counts).
- **Unload after every run [user]:** each `riassumi` loads model + context and frees both before returning
  (`libera` + `llama_backend_free`). Cost: one load per `Riassunto` (0.8–2.8 s warm, 10.7–14.5 s cold). The
  queue shared with the sherpa pipeline (ADR 0023) never holds the LLM's memory while an `Elaborazione` runs.
- **Threads:** llama.cpp defaults; the call runs on the queue's own thread (ADR 0023), never inside a
  transaction (ADR 0021 §4).

### 4. Cancellation, and the bound the port contract states
- `annullato()` is polled by a Kotlin watcher that calls the shim's `annulla` (an atomic flag read by the
  llama.cpp `abort_callback` and between decode steps).
- **Prefill runs in chunks of 512 tokens** (each `llama_decode` is a cancel point). Measured: cancel at 0.8 s
  mid-prefill with 512-token chunks vs 3.4–4.5 s with 2048; 0.05 s during generation. Cost: with 512-token
  chunks the effective ubatch of the prefill is 512 (≈ 190 vs 216 t/s at depth 24k, ≈ +10 s per hour of audio).
  Chosen for the cancel bound; a 1024 chunk is unmeasured.
- The adapter returns `Errore(Annullato)` only **after** `libera` has released the native memory, so the next
  queue item never overlaps it. Release after an abort: 2.1 s measured with 512-token chunks (7.4–7.7 s with
  2048; 2–8 s is the envelope).
- **Contract bound (ADR 0021 §4, `ModelloLinguisticoContratto` under `@Tag("modelli")`): `Errore(Annullato)`
  within 10 s** of `annullato()` becoming true or the thread being interrupted, release included.

### 5. Context budget, output bound, `LimiteIngresso` [user]
- **Every generation is bounded twice (MANDATORY):**
  - a **bounded answer grammar** (GBNF of answer schema v1, compact whitespace): every list ≤ 6 items, every
    `fonti` ≤ 6 ids, ids `[1-9][0-9]{0,5}`;
  - **`max_tokens` = ⌈3.5 × lunghezzaMassimaParole⌉ + 512** (2000 → 7 512; 2500 → 9 262). Reaching it ends the
    run as `RispostaNonValida` (→ `errore_modello`), never a truncated `pronto`.
  
  The prompt's word cap is a request, not a bound: only these two bound the output.
- **Grammar sampling is lazy** (the llama-server strategy): sample without the grammar, check only the chosen
  token, and apply the grammar to the full vocabulary and resample only on rejection (13.3 → 19.4 t/s measured).
- **[INV-S9] upper bound stays 2500 words, default 2000 [user].** Consequence accepted by the user: the context
  grows to ≈ 40k tokens and a `Riassunto` can take up to ≈ 8–10 min per hour of audio (§6).
- **`LimiteIngresso` (single home: `:sintesi:dominio`), calibrated [user]:** estimated tokens =
  **⌈characters of the labelled input ÷ 2.4⌉** (integer form: ⌈5 × characters ÷ 12⌉), compared with the
  **unchanged limit 28 000**. 2.4 is below every measured ratio (2.49–2.51 with `m:ss`; ≈ 2.9 without), so the
  estimate refuses early rather than late. At the limit: 67 200 characters ≈ 1 h 10 of audio without `m:ss`.
- **Consistency with `n_ctx`:** 28 000 (estimate ≥ real) + ≤ 512 system/template + 9 262 (`max_tokens` at 2500) =
  37 774 ≤ 40 960.
- **Exact backstop (run time):** `contaToken` (`llama_tokenize` of the full formatted prompt, 4–27 ms) +
  `max_tokens` > `n_ctx` → `Errore(IngressoTroppoLungo(token))` → `troppo_lunga`, before any decode.

### 6. NFR of a `Riassunto` (its single home; amends the "≤ 300 s" of infra-notes and AC-S153) [user]
- **Measurable AC:** on the Apple M3 Pro 36 GB, model installed, app otherwise idle, a real **60-minute
  `Registrazione`** (Via Roquel), cap at the **default 2000 words**: `RiassuntoAvviato → RiassuntoPronto`, model
  load and unload included, **≤ 600 s**. Recorded by the opt-in `./gradlew benchmarkRiassunto -Pcampione=<…>`
  (outside the gate; fails above 600 s; prints load / prefill / generation / release times and tokens).
- Expected: ≈ 3–4 min typical (measured 185 s: the model wrote 510–1000 words under a 2500 cap); up to ≈ 8–10 min
  when it writes near the cap. Known worst case, not an AC: a run that reaches `max_tokens` at the 2500 cap
  (≈ 9 300 tokens at ≈ 15 t/s ≈ 10 min of generation + ≈ 1.5 min prefill) and ends `errore_modello`.
- ADR 0011's `Elaborazione` budget (≤ 600 s per hour) is unchanged and separate.

### 7. Platforms and packaging: what is open
- **macOS arm64:** proven (`./gradlew run` and `.dmg`, 184.5 s). jpackage's exec-bit loss does not affect JNI
  (no executable is shipped).
- **Windows x64 / Linux x64: OPEN, untested.** Their assets use `GGML_BACKEND_DL` (per-CPU `ggml-cpu-*` variants
  + `ggml-vulkan`): the shim must call `ggml_backend_load_all_from_path(<dir>)` there and be compiled per OS
  (MSVC / gcc). GPU means the Vulkan assets (win 33 MB, linux 31 MB zipped; CPU 19 / 17 MB). Wiring an OS = a row
  of §2 marked verified + the shim built and run there, recorded as an amendment.
- **Signing (ADR 0016 O-2):** if Developer ID signing is chosen, it must cover every Mach-O in the app resources:
  `libllama`, every `libggml*`, and `libsnastro-llama-jni`. Not tested.

### 8. The `:modelli` catalogue entry (the values ADR 0025 §5 deferred) [user]
| Field | Value |
|---|---|
| id | `llm-qwen3.5-9b-q4_k_m` |
| obbligatoria | `false` |
| formato | `FILE` |
| URL | `https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf` |
| dimensioneByte | `6169341984` |
| sha256 | `d784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43` (matches the HF LFS oid) |
| licence | Apache-2.0 (`general.license = apache-2.0`) |
| attribution | "Qwen3.5 9B, Qwen team; quant bartowski" |
| UI size text | "6,2 GB" |

No account and no token (verified). A verified copy for opt-in `modelliTest` / manual runs is at
`~/mangu.snastro/modelli/Qwen_Qwen3.5-9B-Q4_K_M.gguf` (never in the repo).

## Consequences
- **Spike closed.** `modello-linguistico-llama` is unblocked; its `ready_when` is met by this ADR.
- **Follow-up code in already-integrated blocks** (via the owners' rework, not by this ADR):
  `riassunto`'s `LimiteIngresso` (÷ 3 → ÷ 2.4) and `IngressoRiassunto` (no `m:ss`), per the ADR 0021 amendment.
- **Amended in place with dated pointers here:** ADR 0004 (and its check + fixtures), ADR 0021 §4/§5,
  ADR 0025 §5, infra-notes, context-map.
- **Enforcement:**
  - mechanical: ADR 0004's check (native load only in `:ml-sherpa` and `./llm/`); this ADR's check (no GGUF, no
    llama.cpp archive tracked); ADR 0016's (no native library tracked);
  - test, inside the gate: the adapter's parsing on recorded answers (valid, invalid schema, `max_tokens`
    reached), the exact-count backstop arithmetic, the grammar file's bounds;
  - test, opt-in: `ModelloLinguisticoContratto` against the real adapter (cancel bound 10 s), `benchmarkRiassunto`
    (§6);
  - discursive (code review): unload after every run; grammar + `max_tokens` on every call; lazy sampling;
    512-token prefill chunks.
