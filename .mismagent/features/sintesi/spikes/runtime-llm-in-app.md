# Spike evidence — runtime-llm-in-app (returned 2026-09-26, branch spike/runtime-llm-in-app @ 5abb693, never integrated)

Worker recommendation: sidecar llama-server b11195 on a Unix-domain socket, SIGKILL on cancel/completion. Closure = user's decision (ADR via write-adr).

# Spike runtime-llm-in-app — raw evidence (2026-09-26, M3 Pro 36 GB, macOS arm64)

THROWAWAY: this `spike/` tree is never merged. It contains `System.load` and `java.net.*` in `*.kt`
and would fail ADR 0004 and ADR 0008 checks if a gate ran on this branch. Transcript outputs
(`out/`), model weights and natives (`native-cache/`) are gitignored. They hold private meeting content or binaries.

## What was built
| Piece | Path | Role |
|---|---|---|
| Input builder | `tools/prepara_ingresso.py` | The same request for every harness. ADR 0021 §4 input `[s<n> V<n> m:ss] testo` + legend, answer schema v1 (port shape, fonti as ints), 2500-word cap in the prompt |
| Validator | `tools/valida.py` | JSON parse, exact key set, fonti in range, voce ids, `{V<n>}` vs literal names |
| Option A, JNI | `jni/llama_jni.c` (≈150 lines C, built by `jni/build.sh`) + `jvm-harness/Harness.java` | JNI shim over the llama.cpp **C API** of the pinned release: carica / contaToken / genera / annulla / libera. Chunked prefill with a cancel point, `abort_callback`, lazy grammar sampling (the llama-server strategy) |
| Option B, sidecar | `tools/sidecar.py` | Measures `llama-server` over HTTP (apply-template + tokenize = exact count; timings; cancel) |
| Grammars | `jni/risposta-v1*.gbnf` | Hand-written GBNF of schema v1. `-limitata` = ≤12 items/list, ≤8 fonti. `-budget` = ≤6 items/list, ≤6 fonti. Both use compact whitespace |
| Compose app | `app/` (Kotlin 2.1.21, Compose 1.8.2, JBR 21) | BOTH runtimes behind one `Runtime` interface, from `./gradlew run` and from the `.dmg`. The sidecar talks over a **Unix-domain socket** |
| Tools | `tools/gguf_info.py`, `tools/calibra.py` | GGUF header dump; token calibration |

## Pinned artefacts (verified)
- llama.cpp **b11195** (2026-09-26), MIT ("Copyright (c) 2023-2026 The ggml authors").
  - `llama-b11195-bin-macos-arm64.tar.gz`, 11 751 001 B, sha256 `5320d5f90fde78fd046f78c2eab3e0cbde2ccd8b6aa4d3bcd6c15cbbb3672f98`, verified.
  - GitHub digests, NOT run:
    - macos-x64 `df5f585b4b4da45c49b85e1f6a8d8ed9b39871e8d978f0d8add9aa02d0a2b821`;
    - win-cpu-x64 `9416e8bdd133a08a749e71a98435d9b59712e39e914b037ed82f79e2fa8a5093`;
    - win-vulkan-x64 `aa509079b1bbe7bc648e17eab48291379eb11e19853c19855838b46db80f60c9`;
    - ubuntu-x64 `17bcb7c54c99447e58c9b970ae69e7da26d308b37f12a9c486908dd1ae4ed164`;
    - ubuntu-vulkan-x64 `e24e11b3597e5de86ca3c0279e04fd308e75c1a6a5159ab9dbe598c1534577b8`.
- Model: `https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf`.
  - Downloaded with no account and no token.
  - 6 169 341 984 B, sha256 `d784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43`. It matches the HF LFS oid.
  - `general.license = apache-2.0`, imatrix quant.
  - Other Q4_K_M files seen, not downloaded:
    - lmstudio-community @1379f25c…: 5 627 044 256 B, `cd76ec205963b3b33350093e6904d9de16c4e666fd104e1f632d25c7f15f2a13`;
    - unsloth @3885219b…: 5 680 522 464 B, `03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8`.
- **The Ollama blob cannot be reused.** `sha256-dec52a44…` (6.59 GB, the file behind the model-choice measurements) is an Ollama-specific GGUF:
  - it has 441 vision tensors `v.*` and `mtp.*`;
  - `qwen35.rope.dimension_sections` has 3 entries;
  - llama.cpp b11195 refuses it: "wrong array length; expected 4, got 3".
  So the app ships a different quant from the one the model choice measured. The size is 6.2 GB, not 6.6 GB.
- `de.kherud:llama` (java-llama.cpp), whose last release is 4.2.0 of 2025-06-20, was **not run**. It predates the `qwen35` architecture and is unmaintained. Rejected on that basis only.

## Metal (llama-bench, b11195)
- Devices: `MTL0: Apple M3 Pro (28753 MiB)`, BLAS Accelerate.
- At empty context, ngl=99 vs ngl=0: pp2048 **333 vs 68 t/s** (4.9×), tg64 **22.3 vs 17.1 t/s**.
- At depth 24 000:

| n_ubatch | flash-attn | pp1024 | tg128 |
|---|---|---|---|
| 512 | 0 | 190 t/s | 15.8 t/s |
| 512 | 1 | 194 t/s | 15.3 t/s |
| 2048 | 0 | **216 t/s** | **16.9 t/s** |
| 2048 | 1 | 210 t/s | 16.3 t/s |

- Chosen: ubatch 2048, flash-attn auto.
- The mac build links the Metal/CPU/BLAS backends directly. No `ggml_backend_load_all` is needed.

## Via Roquel (1 h, 900 segments, 4 voices): exact input 25 117 tokens (system 295)
Each row is one run. A single seed is deterministic across harness, `./gradlew run` and `.dmg`: the same bytes out.

| Run | Runtime / launch | Grammar | Prefill | Output | Riassumi wall | Schema-valid |
|---|---|---|---|---|---|---|
| sidecar-vr-1 | sidecar, python | json_schema (server) | 87.0 s (289 t/s) | 1785 tok @15.7 | **200.6 s** | yes |
| jni-vr-1 | JNI, java | unbounded GBNF | 97.2 s | **7651 tok → ctx full** | 614 s | **NO** (runaway `fonti` 1,2,3…771) |
| jni-vr-2 = sidecar-vr-2 (seed 42) | both | `-limitata` | 102 / 98 s | 3037 / 3038 tok (all lists hit 12) | 308 / 299 s | yes, 1000 words |
| jni-vr-3 | JNI, java | `-budget`, max 2000 | 94.2 s (267 t/s) | 1408 tok @15.4 | **185.5 s** | yes |
| sidecar-vr-3 | sidecar, python | `-budget`, max 2000 | 95.7 s (263 t/s) | 1726 tok @15.9 | **203.8 s** | yes |
| **dmg-jni-vr** | **JNI, from mounted .dmg** | `-budget` | 90.8 s | 1408 tok | **184.5 s** (+11.9 s cold load) | yes |
| **dmg-sidecar-vr** | **sidecar (UDS), from mounted .dmg** | `-budget` | 91.3 s (275 t/s) | 1726 tok @15.5 | **202.6 s** (+11.6 s cold load) | yes |

- Closure band: 175 s ± 20 % = 140–210 s. Both options fall inside with the budget grammar, the model already loaded.
  - With a cold load, JNI totals 196 s and the sidecar 214 s.
- **The runtimes have the same speed.** Same libllama; prefill ≈ 255–290 t/s, generation ≈ 15–16 t/s at 25k context. The wall time is set by the **number of output tokens**, which sampling and the grammar bounds decide.
- Ollama's reference: 24 137 in, 1363 out in 175 s, with generation at 19.7 t/s. It measured another quant with another engine.
- New Recording 4, 6029 tokens:

| Launch | Runtime | Wall |
|---|---|---|
| python harness | sidecar | 72 s |
| java harness | JNI (lazy grammar) | 144 s: 2441 tokens out |
| `./gradlew run` | sidecar | 78 s |
| `./gradlew run` | JNI | 92 s |

  All four are schema-valid.
- Eager grammar sampling (grammar applied to all 248k logits every token) gave 13.3 t/s. Lazy sampling (sample, check one token, resample on reject) gave 19.4 t/s. The shim must do it lazily.

## Memory (RSS includes the ~5.7 GB file-backed mmap of the weights; footprint = dirty/private incl. KV + compute)
| | Peak RSS | Peak phys footprint | After release |
|---|---|---|---|
| JNI (JVM process) | 7.0–7.7 GB | 1.4–1.8 GB | `libera` 50–180 ms. JVM RSS 150–280 MB (baseline 130), footprint ~170 MB |
| Sidecar (server process) | 7.0–7.4 GB | 1.5–1.8 GB | Process exit 0.08–0.15 s. Everything returns to the OS; the JVM stays at ~45–67 MB |

- **Load time.** Warm page cache: 0.8–2.8 s. Cold: 10.7–14.5 s. It is the same for both options.
- **Unload after each run** costs one load per Riassunto: 1–15 s.

## Cancellation (Via Roquel, cancel at 30 s = mid-prefill, and at 110 s = generating)
| | Prefill | Generation | Release after cancel |
|---|---|---|---|
| JNI, prefill chunk 2048 | 4.5 s (java harness), 3.4 s (.dmg) | 0.05 s | `libera` **7.4–7.7 s** after a mid-prefill abort (Metal drains) |
| JNI, prefill chunk 512 | 0.8 s | — | 2.1 s |
| Sidecar, close the HTTP connection | **does not stop the prefill**: the server finishes all 25k tokens (~55 s later), and `/slots` does not answer meanwhile | 0.12 s | — |
| Sidecar, SIGTERM | **HANGS**: "cleaning up before exit…" then the process sleeps forever (observed 525 s, killed by hand) | — | — |
| Sidecar, **SIGKILL** (`destroyForcibly`) | **0.05 s** (.dmg) | **0.05 s** (.dmg) | immediate: process gone |

- `abort_callback` does fire under Metal at graph boundaries: `graph_compute … failed with error 1` → `llama_decode` returns 2.
- **Orphans observed.** Two `llama-server` processes survived their parent:
  - one when the python harness crashed;
  - one when the JVM died of a StackOverflowError under `./gradlew run`.

  Each held ~1.6 GB of footprint plus the mapped weights, until killed by hand.

## Exact token count, and the calibration for LimiteIngresso / INV-S9 (`out/calibrazione.json`)
- **Exact count.**
  - JNI: `llama_tokenize` of the formatted prompt, 4–27 ms.
  - Sidecar: `/apply-template` + `/tokenize`, 20–30 ms.
  - Both give 25 117 / 6029, identical to the server's `prompt_n`. The hand-written ChatML with an empty `<think></think>` matches the model's jinja template (`enable_thinking=false`).
- **Labelled input (user part only):**
  - Via Roquel: 62 214 characters, 24 805 tokens → **2.51 characters/token**. New Recording 4: 2.49.
  - Qwen splits digits one token each, so `[s123 V2 12:34]` is expensive.
  - The provisional `⌈characters ÷ 3⌉` UNDER-estimates by about 17 %. It is not conservative. With a limit of 28 000 it admits about 33 600 real tokens, more than 32 768 of context.
- **Without `m:ss` in the line:** −21.3 % input tokens (24 805 → 19 515), which is about −20 s of prefill on 1 h.
- **Answer tokens per Italian word:**
  - prose only: **1.42–1.49**;
  - whole JSON (keys, fonti ids, punctuation): **2.5–3.5**, 3.0 typical with compact whitespace.
- **The output budget at a 32 768 context:** 32 768 − 28 000 (input limit) − ~310 (system + template) ≈ **4 450 tokens ≈ 1 480 words** at 3.0 tokens/word (1 270 at 3.5). **2500 words does not fit** at the maximum input. It needs n_ctx ≈ 36–40k. The KV cost is small: the hybrid model has only 8 attention layers.
- **The time budget binds before the context does.** At 15 t/s, 2500 words × 3.0 = 7 500 tokens = ~500 s of generation alone.
  - ≤ 300 s (infra-notes NFR) → ~3 000 output tokens ≈ **1 000 words**.
  - ≤ 210 s (the closure band) → ~1 700 tokens ≈ **570 words**.
- The prompt's cap is a request, not a bound. The measured runs wrote 510–1000 words under a 2500 cap. Only grammar bounds plus `max_tokens` bound the output.

## Packaging
- **mac arm64 natives.**
  - JNI set: libllama 3.2 MB + ggml-base/cpu/blas/metal/rpc/ggml 6.6 MB + shim 0.05 MB ≈ **10.6 MB**.
  - Sidecar set: the same + llama-server (0.05) + libllama-server-impl (8.9) + libllama-common (7.6) + libmtmd (1.4) ≈ **28 MB**.
- `.dmg` with both options and the JBR runtime: 82 MB; the `.app` is 162 MB.
- **jpackage drops the exec bit** of `appResources`. `llama-server` arrived `-rw-r--r--` and exec failed with EACCES. Fixed by `chmod` in `createDistributable.doLast`, and `packageDmg` keeps it. `codesign --verify --deep --strict` passes (adhoc).
- **Signing.** The release dylibs and binary are adhoc/linker-signed, and jpackage re-signs adhoc. With Developer ID (O-2), every Mach-O must be signed: the dylibs, the shim, and `llama-server` as a nested executable with hardened runtime. Not tested.
- **Windows/Linux** (asset listings, not run):
  - they use `GGML_BACKEND_DL` (per-CPU `ggml-cpu-*.dll/.so` variants plus `ggml-vulkan`);
  - JNI would have to call `ggml_backend_load_all_from_path` and compile the shim per OS (MSVC/gcc);
  - the sidecar runs `llama-server(.exe)` as shipped;
  - GPU there means the Vulkan assets (win 33 MB, linux 31 MB zipped); CPU assets 19 MB / 17 MB;
  - llama-server's UNIX-socket listening on Windows was not verified. The fallback is 127.0.0.1 with a random `--api-key`.
- **Unix-domain socket:** `llama-server --host <path>.sock` works. There is no TCP listener (`lsof -i` is empty). The JDK client is `SocketChannel.open(StandardProtocolFamily.UNIX)` + `UnixDomainSocketAddress`, and the socket sits in a `0700` temp dir. Limit: `sun_path` ≤ 104 bytes on macOS, so a long path failed with "Unix domain path too long". Use `java.io.tmpdir`, not deep dirs.

## Kotlin/JVM integration cost (as prototyped)
- **JNI.**
  - ≈150 lines of C against `llama.h` of the pinned tag. The ABI of `llama_context_params` / `llama_model_params` changes often, so every bump means recompiling and re-checking.
  - ≈25 lines of Kotlin `external` + watcher.
  - The build needs clang + JDK headers (mac), and MSVC/gcc elsewhere. That is a compiled native, not a fetched asset (ADR 0016 fetches pinned assets only).
  - One `System.load` in `./llm/`.
- **Sidecar.**
  - ≈70 lines of Kotlin: ProcessBuilder + a 15-line HTTP/1.1-over-UDS client + JSON. The block would use a JSON library; the spike hand-decodes.
  - No compile step; the release binaries are used unmodified.
  - Lifecycle code: SIGKILL on cancel and close, pidfile sweep at start, shutdown hook.

## The spike's recommendation (the decision is the user's)
**Sidecar `llama-server` b11195 over a Unix-domain socket, spawned per Riassunto, killed with SIGKILL on cancel or completion.**
- **Why.**
  - The speed equals JNI.
  - Cancellation is 0.05 s in every phase.
  - Memory release is total.
  - A llama.cpp abort cannot kill the app.
  - No native code is compiled by us: the pinned release assets are used as-is on every OS.
  - The coupling is an HTTP API, not the C struct ABI.
- **Risks.**
  - Orphan processes if the JVM dies. Observed; mitigated by a pidfile sweep at start plus a shutdown hook.
  - SIGTERM hangs, so the adapter must always SIGKILL.
  - The exec bit needs a packaging fix, and signing must cover a nested executable.
  - +18 MB.
  - An ADR 0008 amendment is needed (UDS client in `./llm/`).
- **JNI is viable too**, with the same numbers. It costs a per-OS C shim and a 0.8–4.5 s prefill-cancel latency with slow release, and it needs an ADR 0004 amendment instead.
- **Both options** need the answer's grammar bounded plus a `max_tokens` cap: an unbounded grammar ran away once in 5 runs.
