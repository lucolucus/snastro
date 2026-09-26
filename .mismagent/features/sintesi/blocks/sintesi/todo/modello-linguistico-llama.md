---
id: "modello-linguistico-llama"
type: "adapter"
context: "sintesi"
side: "app"
wave: 7
release: "R3"
module: ":llm (new) + :sintesi:adattatori (..ml) + catalogue entry in :modelli + avvio-sintesi binding + packaging"
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
ready_when: "ADR closing spike runtime-llm-in-app accepted (JNI vs llama-server sidecar, :llm content, natives per ADR 0016, cancellation bound, keep-warm vs unload, LimiteIngresso / INV-S9 calibration, ADR 0025 §5 catalogue entry values)"
model_hint: "deep"
tests_nl_status: "draft"
---
# modello-linguistico-llama — Adattatore reale ModelloLinguistico su runtime llama (JNI o sidecar, deciso dallo spike)

## What to do
Create :llm per the spike ADR and the ..ml adapter implementing ModelloLinguistico (prompt + answer schema v1, the cap and the Argomento instruction, translation of the prompt's speaker syntax to {V<n>}, cancellation, unload after each run unless the spike says keep-warm); add the optional catalogue entry with the spike's values; fetch natives at build time per ADR 0016; amend exactly one trunk enforced_by as the spike ADR decides; replace the placeholder binding in avvio-sintesi; add benchmarkRiassunto.

Note: GATED by the open spike runtime-llm-in-app; spikes qualita-riassunto (schema v1/prompt, cap adherence, [300, 2500]) and filtro-fuori-tema (Argomento wording/bound) refine its prompt and may recalibrate LimiteIngresso / INV-S9 / Argomento bound — through their ADRs, never silently.

**ready_when:** ADR closing spike runtime-llm-in-app accepted (JNI vs llama-server sidecar, :llm content, natives per ADR 0016, cancellation bound, keep-warm vs unload, LimiteIngresso / INV-S9 calibration, ADR 0025 §5 catalogue entry values) — the worker-composer does not dispatch this block before that.

## Tasks
- AC-S152 [@modelli] ModelloLinguisticoContratto passes against the real adapter: complete structure, speakers only as {V<n>}, Errore(Annullato) within the spike's measured bound
- AC-S153 [@modelli] NFR: a 60-minute Registrazione (≈ 25 k input tokens) completes RiassuntoAvviato → RiassuntoPronto in ≤ 300 s on the Apple M3 Pro reference machine; `./gradlew benchmarkRiassunto -Pcampione=<fixture>` records it (opt-in, outside the gate)
- AC-S154 The exact runtime token count over the limit → Errore(IngressoTroppoLungo(token)); a runtime failure → ErroreRuntime; an answer not matching schema v1 → RispostaNonValida (unit tests on the adapter's parsing with recorded answers, inside the gate)
- AC-S155 The catalogue gains the optional entry (obbligatoria = false) with the spike's immutable Hugging Face URL at a commit, sha256, dimensioneByte, id, Apache-2.0 licence and attribution; pronti() unaffected
- AC-S156 Natives are fetched at BUILD time into native-cache/ with pinned coordinates + SHA-256 (ADR 0016); the running app downloads weights only; the trunk enforced_by amended by the spike ADR (ADR 0004 System.load or ADR 0008 loopback-only 127.0.0.1) exits 0
- AC-S157 allowedModuleEdges gains :llm → :kernel, :modelli and :sintesi:adattatori → :llm; avvio-sintesi binds the real adapter instead of the placeholder; the model is unloaded after each run unless the spike ADR decides keep-warm (peak RSS reported, no ceiling pinned yet)

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

Sources: ADR 0021 §4–5, ADR 0023 §5–6, ADR 0025 §5, tasks/app/todo/runtime-llm-in-app.md; related_adrs 0004, 0008, 0011, 0012, 0016, 0021, 0023, 0025; tactical-model: features/sintesi/tactical-model.md
