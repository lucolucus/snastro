---
id: "modello-linguistico-llama"
type: "adapter"
context: "sintesi"
side: "app"
wave: 7
release: "R3"
module: ":sintesi:adattatori (..ml: ModelloLinguistico over :llama-jni) + catalogue entry in :modelli + :avvio (avvio-sintesi binding, model path + native dir suppliers, copy of :llama-jni:assembleNatives into appResourcesRootDir/<os-arch>/, benchmarkRiassunto)"
consumes:
  - "tec-modello-linguistico"
  - "tec-modelli-facoltativo"
  - "tec-llama-jni"
related_adrs:
  - "0004"
  - "0008"
  - "0011"
  - "0016"
  - "0021"
  - "0023"
  - "0025"
  - "0026"
  - "0027"
ready_when: "SATISFIED 2026-09-26 — ADR 0026 accepted (closes spike runtime-llm-in-app [user]: llama.cpp b11195 in-process via JNI, natives, cancel bound 10 s, unload after every run, LimiteIngresso ÷ 2.4 / INV-S9 2500, catalogue values); ADR 0027 accepted with the user's Q-1..Q-4 answers (D-0007)"
model_hint: "deep"
tests_nl_status: "draft"
---
# modello-linguistico-llama — Adattatore reale ModelloLinguistico sulla libreria llama-jni (ADR 0026, ADR 0027 §7)

## What to do
Implement the ..ml adapter of ModelloLinguistico in :sintesi:adattatori over the :llama-jni library's interfaces (ADR 0027 §7): ChatML prompt with an empty <think></think>, speaker legend and {V<n>} translation, the Argomento instruction, answer schema v1 as a bounded GBNF, maxTokens = ⌈3.5 × cap⌉ + 512, JSON parsing to RispostaModello, ModelParams(nCtx 40 960, nUbatch 2048, prefillChunk 512), open → generate → close every run, GPU→CPU retry once, the error mapping of ADR 0027 §7; add the optional catalogue entry in :modelli (ADR 0026 §8); in :avvio bind the real adapter instead of the placeholder in avvio-sintesi, supply the model path and native dir lazily, copy :llama-jni:assembleNatives into appResourcesRootDir/<os-arch>/ outside check; add benchmarkRiassunto.

Note: Slimmed 2026-09-26 by ADR 0027 (user; D-0007): the JNI binding is the separate library :llama-jni (package io.github.lucolucus.llamajni, block llama-jni-libreria; Windows/Linux wiring llama-jni-windows / llama-jni-linux, R4). No :llm module is ever created. This block is the snastro side only: :sintesi:adattatori ..ml implements ModelloLinguistico over the library's interfaces (ChatML with an empty <think></think>, the Riassunto speaker legend and {V<n>} translation, the Argomento instruction, answer schema v1 as a bounded GBNF, maxTokens from the cap, JSON parsing to RispostaModello, n_ctx 40 960 / n_ubatch 2048 / prefill chunk 512, lazy grammar, open → generate → close every run, GPU→CPU retry once); the :modelli catalogue entry; :avvio's binding, location suppliers, the copy of :llama-jni:assembleNatives (task names downloadLlamaNatives / compileJniShim / assembleNatives, cache <Gradle user home>/caches/llama-jni/b11195/, shim libllamajni) and benchmarkRiassunto. :sintesi:* still has no edge to :modelli (ADR 0021). Only macOS arm64 is wired (Q-1); the 600 s NFR is the Mac's (Q-4). Spikes qualita-riassunto (schema v1/prompt, cap adherence, [300, 2500]) and filtro-fuori-tema (Argomento wording/bound) refine its prompt and may recalibrate LimiteIngresso / INV-S9 / Argomento bound — through their ADRs, never silently.

**ready_when:** SATISFIED 2026-09-26 — ADR 0026 accepted (closes spike runtime-llm-in-app [user]: llama.cpp b11195 in-process via JNI, natives, cancel bound 10 s, unload after every run, LimiteIngresso ÷ 2.4 / INV-S9 2500, catalogue values); ADR 0027 accepted with the user's Q-1..Q-4 answers (D-0007).

## Tasks
- AC-S152 [@modelli] ModelloLinguisticoContratto passes against the real adapter over :llama-jni with Qwen3.5 9B: complete structure, speakers only as {V<n>}, Errore(Annullato) within 10 s of annullato() becoming true or the thread being interrupted, the model closed before returning (the adapter passes annullato as the library's cancel(); 512-token prefill chunks, ADR 0026 §4)
- AC-S153 [@modelli] NFR: on the Apple M3 Pro 36 GB (model installed, app otherwise idle) a real 60-minute Registrazione (Via Roquel) at the default 2000-word cap completes RiassuntoAvviato → RiassuntoPronto, model load and unload included, in ≤ 600 s; `./gradlew benchmarkRiassunto -Pcampione=<fixture>` records it (opt-in, outside the gate; fails above 600 s; prints load / prefill / generation / release times and tokens) (ADR 0026 §6; Mac only — no Windows/Linux budget, Q-4)
- AC-S154 The exact runtime token count over the limit → Errore(IngressoTroppoLungo(token)); a runtime failure → ErroreRuntime; an answer not matching schema v1 → RispostaNonValida (unit tests on the adapter's parsing with recorded answers, inside the gate)
- AC-S155 The catalogue gains the optional entry (ADR 0026 §8): id 'llm-qwen3.5-9b-q4_k_m', obbligatoria = false, formato FILE, URL 'https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf', dimensioneByte 6 169 341 984, sha256 'd784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43', licence Apache-2.0, attribution 'Qwen3.5 9B, Qwen team; quant bartowski'; the UI size text derived from dimensioneByte reads '6,2 GB'; pronti() unaffected
- AC-S156 (rewritten 2026-09-26, ADR 0027 §3: the native tasks are the library's, AC-S164) :avvio wiring only: :avvio copies :llama-jni:assembleNatives' output into its appResourcesRootDir/<os-arch>/ (ADR 0016 §3) for :avvio:run, createDistributable, prepareAppResources, modelliTest and benchmarkRiassunto; the .app built by createDistributable contains libllamajni.dylib and the llama/ggml libraries under its resources; none of these copies is a dependency of check (`./gradlew check --dry-run` lists no assembleNatives); the running app downloads weights only
- AC-S157 (rewritten 2026-09-26, ADR 0027) allowedModuleEdges: :sintesi:adattatori gains :llama-jni and NO :llm row or edge exists (a throwaway :sintesi:adattatori → :modelli edge still fails verificaDipendenzeModuli); every riassumi call does openModel → generate → close on the library, close() called on success, error and cancel alike, no keep-warm (fake LlamaBackend/LlamaModel records the sequence, inside the gate); avvio-sintesi binds the real adapter instead of the placeholder (test on the built graph); :avvio supplies the model path (from :modelli's installed GGUF) and the native dir as suppliers read lazily at the first run; peak RSS reported by benchmarkRiassunto, no ceiling pinned yet
- AC-S159 (adapter half; generic half AC-S165) Output bound (ADR 0026 §5): every generation passes the bounded answer grammar (GBNF of schema v1: every list ≤ 6 items, every fonti ≤ 6 ids, ids [1-9][0-9]{0,5}) and maxTokens = ⌈3.5 × lunghezzaMassimaParole⌉ + 512 (2000 → 7 512; 2500 → 9 262) in GenerateOptions; Generation.stop = MAX_TOKENS → Errore(RispostaNonValida) (→ errore_modello), never a truncated answer (inside the gate: the maxTokens arithmetic, the grammar file's bounds, a fake model returning MAX_TOKENS)
- AC-S160 (adapter half; generic half AC-S166) Exact backstop (ADR 0026 §5): the adapter opens with ModelParams(nCtx 40 960, nUbatch 2048, prefillChunk 512) and maps the library's Err(ContextOverflow(p, m, n)) → Errore(IngressoTroppoLungo(p)) (→ troppo_lunga) (inside the gate, fake library)
- AC-S179 (NEW, ADR 0027 §4 GPU→CPU policy) Fake library, inside the gate: when devices reports a GPU the adapter opens with nGpuLayers = ALL; if that open fails (ModelLoadFailed or ContextCreateFailed) it retries ONCE in the same run with nGpuLayers = 0; a second failure → Errore(ErroreRuntime(motivo)); no GPU device → opens with nGpuLayers = 0 directly (one open); no retry on ContextOverflow, Cancelled or any generate error
- AC-S180 (NEW, ADR 0027 §7 error mapping and locations) Fake library, inside the gate: Cancelled → Annullato; MAX_TOKENS or schema mismatch → RispostaNonValida; every other LlamaError → ErroreRuntime(motivo); model file missing → ModelloNonDisponibile; native dir: snastro.llm.native.path if set, else compose.application.resources.dir, neither → ErroreRuntime naming BOTH properties; the adapter's prompt is ChatML with the empty <think></think>, the speaker legend with {V<n>} and the Argomento instruction (asserted on the text handed to generate)

## Dependencies
- **tec-modello-linguistico** (consumed; owner modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `ModelloLinguistico`: interface { fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> } — blocking; NEVER called inside a UnitaDiLavoro transaction
  - `RichiestaRiassunto`: data class(ingresso: String, argomento: String?, lunghezzaMassimaParole: Int)
  - `RispostaModello`: data class(sommario: String?, decisioni: List<ElementoRisposta>, questioniAperte: List<ElementoRisposta>, azioni: List<AzioneRisposta>, puntiChiave: List<PuntoChiaveRisposta>) — raw, UNVERIFIED; speakers only as {V<n>}
  - `ElementoRisposta`: data class(testo: String, fonti: List<Int>)
  - `AzioneRisposta`: data class(testo: String, fonti: List<Int>, responsabile: Int?)
  - `PuntoChiaveRisposta`: data class(testo: String, fonti: List<Int>, parlante: Int?)
  - `ErroreApplicazioneSintesi`: sealed : ErroreDominio { ModelloNonDisponibile; IngressoTroppoLungo(token: Int); ErroreRuntime(motivo: String); RispostaNonValida; Annullato } → motivo: modello_non_disponibile, troppo_lunga, errore_modello, errore_modello, (nothing written)
  - key `fonti / responsabile / parlante`: segmentoId / voceId NUMBERS of the Trascritto generation the input was built from (Published Language integers); validity is NOT the port's promise — the root checks it (INV-S4)
- **tec-modelli-facoltativo** (consumed; owner modelli-provisioning-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `VoceCatalogo`: + obbligatoria: Boolean = true (every other field unchanged)
  - `ProvisioningModelli`: + fun installata(id: String): Boolean; + fun scarica(id: String, progresso: (scaricati: Long, totali: Long) -> Unit): Esito<Unit>; pronti()/mancanti() range over obbligatoria entries only
  - `ErroreModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `VoceCatalogo.id (optional LLM)`: minted by the runtime-llm-in-app spike ADR (e.g. 'llm-qwen3.5-9b-q4_k_m'); any SHA-256 change mints a new id (ADR 0008 (c)(2)); URL immutable (HF …/resolve/<commit-sha>/<file>)
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
- avvio-sintesi — build dependency (merged before this block)
- llama-jni-libreria — build dependency (merged before this block)

Sources: ADR 0026 §1–§8 (as amended 2026-09-26), ADR 0027 §2–§4, §7, ADR 0021 §4–5 (+ amendment 2026-09-26), ADR 0004 amendment 2026-09-26 (b), ADR 0023 §5–6, ADR 0025 §5 (+ amendment 2026-09-26), tasks/app/done/runtime-llm-in-app.md, decisions.md D-0007; related_adrs 0004, 0008, 0011, 0016, 0021, 0023, 0025, 0026, 0027; tactical-model: features/sintesi/tactical-model.md
