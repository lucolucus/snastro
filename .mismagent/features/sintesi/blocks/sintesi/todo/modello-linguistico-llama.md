---
id: "modello-linguistico-llama"
type: "adapter"
context: "sintesi"
side: "app"
wave: 7
release: "R3"
module: ":llm (new: JNI shim + external declarations, ADR 0026) + :sintesi:adattatori (..ml) + catalogue entry in :modelli + avvio-sintesi binding + build-time natives/packaging"
consumes:
  - "tec-modello-linguistico"
  - "tec-modelli-facoltativo"
related_adrs:
  - "0004"
  - "0008"
  - "0011"
  - "0016"
  - "0021"
  - "0023"
  - "0025"
  - "0026"
ready_when: "SATISFIED 2026-09-26 — ADR 0026 accepted (closes spike runtime-llm-in-app [user]: llama.cpp b11195 in-process via JNI, natives, cancel bound 10 s, unload after every run, LimiteIngresso ÷ 2.4 / INV-S9 2500, catalogue values)"
model_hint: "deep"
tests_nl_status: "draft"
---
# modello-linguistico-llama — Adattatore reale ModelloLinguistico su llama.cpp in-process via JNI (ADR 0026)

## What to do
Create :llm per ADR 0026 (JNI shim over llama.cpp b11195, external functions in snastro.llm, one lazy System.load) and the ..ml adapter implementing ModelloLinguistico (ChatML prompt + answer schema v1 as a bounded GBNF grammar, max_tokens from the cap, the Argomento instruction, translation of the prompt's speaker syntax to {V<n>}, the exact-count backstop, cancellation within 10 s, unload after every run); add the optional catalogue entry with ADR 0026 §8's values; add scaricaNativiLlama + compilaShimLlama outside check (ADR 0026 §2); replace the placeholder binding in avvio-sintesi; add benchmarkRiassunto.

Note: Runtime per ADR 0026 [user 2026-09-26]: llama.cpp b11195 used as released + our C shim (carica / contaToken / genera / annulla / libera) with vendored b11195 headers; the shim, the external declarations and the single System.load live only in ./llm/ (ADR 0004 amended); no socket/HTTP in :llm (ADR 0008 unchanged). Execution: Metal all layers, n_ubatch 2048, n_ctx 40 960, prefill in 512-token chunks, lazy grammar sampling, ChatML with an empty <think></think>; natives loaded lazily from snastro.llm.native.path or compose.application.resources.dir (neither → clear failure naming both). Only macOS arm64 is wired and verified; Windows/Linux stay OPEN (ADR 0026 §7). Spikes qualita-riassunto (schema v1/prompt, cap adherence, [300, 2500]) and filtro-fuori-tema (Argomento wording/bound) refine its prompt and may recalibrate LimiteIngresso / INV-S9 / Argomento bound — through their ADRs, never silently.

**ready_when:** SATISFIED 2026-09-26 — ADR 0026 accepted (closes spike runtime-llm-in-app [user]: llama.cpp b11195 in-process via JNI, natives, cancel bound 10 s, unload after every run, LimiteIngresso ÷ 2.4 / INV-S9 2500, catalogue values).

## Tasks
- AC-S152 [@modelli] ModelloLinguisticoContratto passes against the real adapter: complete structure, speakers only as {V<n>}, Errore(Annullato) within 10 s of annullato() becoming true or the thread being interrupted, native release included (ADR 0026 §4: 512-token prefill chunks)
- AC-S153 [@modelli] NFR: on the Apple M3 Pro 36 GB (model installed, app otherwise idle) a real 60-minute Registrazione (Via Roquel) at the default 2000-word cap completes RiassuntoAvviato → RiassuntoPronto, model load and unload included, in ≤ 600 s; `./gradlew benchmarkRiassunto -Pcampione=<fixture>` records it (opt-in, outside the gate; fails above 600 s; prints load / prefill / generation / release times and tokens) (ADR 0026 §6)
- AC-S154 The exact runtime token count over the limit → Errore(IngressoTroppoLungo(token)); a runtime failure → ErroreRuntime; an answer not matching schema v1 → RispostaNonValida (unit tests on the adapter's parsing with recorded answers, inside the gate)
- AC-S155 The catalogue gains the optional entry (ADR 0026 §8): id 'llm-qwen3.5-9b-q4_k_m', obbligatoria = false, formato FILE, URL 'https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf', dimensioneByte 6 169 341 984, sha256 'd784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43', licence Apache-2.0, attribution 'Qwen3.5 9B, Qwen team; quant bartowski'; the UI size text derived from dimensioneByte reads '6,2 GB'; pronti() unaffected
- AC-S156 Natives (ADR 0026 §2, ADR 0004 amendment 2026-09-26): scaricaNativiLlama fetches the host's llama.cpp b11195 asset at BUILD time into native-cache/llama-b11195/ with pinned URL + SHA-256 (mismatch: fail, delete) and compilaShimLlama builds libsnastro-llama-jni; both feed :avvio:run, createDistributable, prepareAppResources, modelliTest and benchmarkRiassunto and are NOT dependencies of check (the gate runs no native code and no C compiler); the running app downloads weights only; ADR 0004's amended check (native loading only in ml-sherpa and ./llm/) and ADR 0026's check (no GGUF / llama.cpp archive tracked) exit 0
- AC-S157 allowedModuleEdges gains :llm → :kernel, :modelli and :sintesi:adattatori → :llm; avvio-sintesi binds the real adapter instead of the placeholder; every riassumi call loads model + context and frees both (libera + llama_backend_free) before returning, on success, error and cancel alike — no keep-warm (ADR 0026 §3); peak RSS reported, no ceiling pinned yet
- AC-S159 Output bound (ADR 0026 §5): every generation runs with the bounded answer grammar (GBNF of schema v1: every list ≤ 6 items, every fonti ≤ 6 ids, ids [1-9][0-9]{0,5}) and max_tokens = ⌈3.5 × lunghezzaMassimaParole⌉ + 512 (2000 → 7 512; 2500 → 9 262); a run that reaches max_tokens → Errore(RispostaNonValida) (→ errore_modello), never a truncated answer (inside the gate: the max_tokens arithmetic, the grammar file's bounds, the parsing of a recorded max_tokens-reached answer)
- AC-S160 Exact backstop (ADR 0026 §5): before any decode, the exact token count of the full formatted prompt (contaToken) + max_tokens > n_ctx 40 960 → Errore(IngressoTroppoLungo(token)) (→ troppo_lunga) with no decode; a sum of exactly 40 960 proceeds (inside the gate: the backstop arithmetic with injected counts)

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
- avvio-sintesi — build dependency (merged before this block)

Sources: ADR 0026 §1–§8, ADR 0021 §4–5 (+ amendment 2026-09-26), ADR 0004 amendment 2026-09-26, ADR 0023 §5–6, ADR 0025 §5 (+ amendment 2026-09-26), tasks/app/done/runtime-llm-in-app.md; related_adrs 0004, 0008, 0011, 0012, 0016, 0021, 0023, 0025, 0026; tactical-model: features/sintesi/tactical-model.md
