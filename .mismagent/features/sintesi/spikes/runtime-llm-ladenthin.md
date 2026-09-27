# Spike runtime-llm-ladenthin: can the published `net.ladenthin:llama` replace our `:llama-jni`?

Opened 2026-09-27 [user]. Status: **static analysis done, measurements on the M3 Pro still to run.**
Branch `claude/qwen-custom-library-d2k6ao`.

## Question
ADR 0026 §1 rejected every published Java binding and built our own JNI shim (then the library `:llama-jni`, ADR 0027).
The only binding it looked at was `de.kherud:llama` 4.2.0 (2025-06-20), which predates the `qwen35` architecture.
It missed the maintained fork of that project:
- `net.ladenthin:llama` ([bernardladenthin/java-llama.cpp](https://github.com/bernardladenthin/java-llama.cpp), MIT);
- release **5.1.0** on Maven Central on 2026-08-29, a month before ADR 0026.

Can this fork run Qwen3.5 9B under ADR 0026's constraints, so that we can drop the C shim, the vendored headers and the
per-OS native build?

## What was built (throwaway, spike only)
| Piece | Path | Role |
|---|---|---|
| Adapter | `sintesi/adattatori/.../ml/ModelloLinguisticoLadenthin.kt` | Same port as `ModelloLinguisticoLlama`, over the fork. It reuses the **same** `PromptRiassunto`, `GrammaticaRisposta` (bounded GBNF v1), `RispostaV1` and `maxTokens`. Same context 40 960, 512-token prefill batches, sampler top-k 40 / top-p 0.95 / temperature 0.2 / seed 42. Exact token count before the call. `MAX_TOKENS` → `RispostaNonValida`. |
| Selection | `-Dsnastro.llm.runtime=ladenthin` (`modelloLinguisticoR3`); `-Pruntime=ladenthin` (`benchmarkRiassunto`, `run`, `packageDmg`) | Without the flag, nothing changes: `:llama-jni` stays the adapter. |
| Opt-in test | `ModelloLinguisticoLadenthinModelliTest` (`@Tag("modelli")`) | `ModelloLinguisticoContratto` + cancellation during generation (flag and interrupt) + **cancellation during the prefill** of a ≈ 20k-token input. Bound: 10 s, release included (ADR 0026 §4). |

Gate on this branch: `:sintesi:adattatori` compiles and passes detekt and its tests, `:llama-jni:check` passes, and
all ADR scripts pass. `:avvio` could **not** be compiled in the container, because Google's Maven repository (Compose,
androidx) is blocked there. Its changes are small: the flag in `ModelloLinguisticoR3`, `BenchmarkRiassuntoTest` and
`build.gradle.kts`. They must be compiled on the Mac.

## Findings from the jar and the sources (verified, 2026-09-27)
1. **Qwen3.5 is supported.**
   - The 5.1.0 natives report llama.cpp build **`b10682-5ea1b124e`** (loaded here on Linux x86_64).
   - The macOS arm64 dylib contains `llama_model_qwen35` / `qwen35moe`.
   - Note: the README badge says b11222, but that is unreleased `main`. The release is b10682, **older** than our
     b11195. Whether b10682 loads the pinned bartowski GGUF is measurement M1.
2. **BLOCKER for the `.dmg`: the macOS dylib hard-links Homebrew OpenSSL.**
   - `Mac/aarch64/libjllama.dylib` has non-weak `LC_LOAD_DYLIB` entries for
     `/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib` and `libcrypto.3.dylib`.
   - The cause is `LLAMA_CURL ON` in their CMake, for model downloads we would never use.
   - On a Mac without Homebrew's `openssl@3` at that exact path, the library does not load. The dev M3 probably has
     it, so a local run would **hide** the problem.
   - Linux x86_64 is clean (only libc/libstdc++/libgomp). Windows builds BoringSSL in.
   - Workarounds all break "used as released":
     - bundle libssl/libcrypto and rewrite the install names (this invalidates the ad-hoc signature);
     - build the fork ourselves with `LLAMA_CURL=OFF`;
     - get it fixed upstream.
3. **The macOS dylib requires macOS 15** (`LC_BUILD_VERSION minos 15.0`). Our b11195 release assets have no such
   floor. snastro's supported macOS versions are not stated anywhere yet.
4. **Cancellation during the prefill is only possible by closing the model.**
   - The fork is an embedded llama-server. `cancelCompletion(taskId)` only drops the reader from a map. A call blocked
     in `receiveCompletionJson` still holds the reader, so it keeps waiting until the first token. That is after the
     whole prefill: ≈ 90 s on Via Roquel.
   - `LlamaModel.close()` sets a `closing` flag that the blocked read polls (≈ 1 s), then stops the server worker
     between two batches.
   - The adapter therefore cancels by closing the model from a watcher thread. It matches ADR 0026 §3 ("unload after
     every run") but has not been measured. That is measurement M3.
5. **Its llama.cpp is patched** (8 patches in `llama/patches/`: server embedding, log sink, progress callback…).
   ADR 0026 says "used as released: we never patch llama.cpp". Here someone else patches it.
6. **Size and dependencies.**
   - The jar is 76 MB and carries every OS; the macOS dylib is 17.9 MB, against ≈ 10.6 MB for ours.
   - Transitive dependencies: jackson-databind 2.22, slf4j-api, jspecify, checker-qual, and a **runtime logback
     binding** (excluded in the spike).
   - The library also contains an OpenAI-compatible HTTP server and curl downloads. We never call them, but they are
     inside the process. ADR 0008 (network only in `:modelli`) is not violated by our code, and its check passes.
7. **Natives.**
   - By default they are extracted from the jar into `java.io.tmpdir` at first use.
   - `-Dnet.ladenthin.llama.lib.path=<dir>` loads from an explicit directory instead, so the ADR 0016 layout is
     possible.
   - There is no pinned SHA-256 of our own, only Maven Central's signatures. Jar sha256:
     `14fe789ee06a679f116554ec7f8e03761926d57e339e17acedd4dba37995d4a6`.
8. **What it does well:**
   - lazy grammar sampling is the server's own (the strategy we copied);
   - `n_predict` stops with `StopReason.MAX_TOKENS`;
   - timings and token counts come back with the result;
   - it has a Kotlin module and Windows/Linux/Vulkan/CUDA builds we would not have to compile;
   - it is very active: last commit 2026-09-27.

## Measurements still to run (M3 Pro, same model file as ADR 0026 §8)
```sh
# M0: will the dylib load on a Mac WITHOUT Homebrew openssl? (expect: NO, finding 2)
ls -l /opt/homebrew/opt/openssl@3/lib/libssl.3.dylib

export SNASTRO_MODELLO_LLM=~/mangu.snastro/modelli/Qwen_Qwen3.5-9B-Q4_K_M.gguf
# M1 + M3: contract, cancellation during generation and DURING THE PREFILL (bound 10 s, release included)
./gradlew :sintesi:adattatori:modelliTest --tests '*Ladenthin*'
# M2: Via Roquel end to end, fork vs :llama-jni (same prompt, grammar, sampling)
./gradlew :avvio:benchmarkRiassunto -Pcampione=<Via Roquel .md> -Pruntime=ladenthin
./gradlew :avvio:benchmarkRiassunto -Pcampione=<Via Roquel .md>
# M4: from the .dmg
./gradlew :avvio:packageDmg -Pruntime=ladenthin
```
| # | Measure | Pass if |
|---|---|---|
| M1 | b10682 loads the bartowski GGUF, schema-valid answer | contract green |
| M2 | Via Roquel `RiassuntoAvviato → RiassuntoPronto`, peak RSS | ≤ 600 s (ADR 0026 §6), and close to the 184.5 s of `:llama-jni` |
| M3 | cancel mid-prefill and mid-generation, release included | ≤ 10 s (ADR 0026 §4) |
| M4 | `.dmg` runs a Riassunto, **also on a Mac without Homebrew openssl@3** | runs |

## Preliminary assessment (before measurements)
Do not replace `:llama-jni` with 5.1.0 as published.

Finding 2 alone breaks the `.dmg` for users without Homebrew. Removing it means rebuilding or relinking the native
library, and that brings back the per-OS native build the swap was meant to remove, on a much larger code base. Add
the macOS 15 floor, the patched llama.cpp, the older llama.cpp release and a cancellation that exists only as "close
the model".

Worth revisiting if upstream ships a macOS build without curl/OpenSSL. If M1–M3 then pass, the swap becomes
attractive, mostly for Windows/Linux, which ADR 0027 §4 otherwise makes us build ourselves.

Either way, **ADR 0026's Context must be corrected**: it should name the fork, its release and the reason it was
set aside. Today it says no maintained binding supported `qwen35`, and that was not true on 2026-09-26.
