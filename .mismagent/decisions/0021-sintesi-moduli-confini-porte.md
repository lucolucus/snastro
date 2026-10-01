---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: architecture.md (module map + edges + boundaries), ADR 0006 enforced_by (deny-list gains `llm`), ADR 0018 §5 (TrascrittoSostituito gains a Sintesi synchronous consumer), code-rules.md CR-3 / CR-10, dev-architecture-app.md (physical deletions list)
closes_spike: null
amended: 2026-09-27   # see §10 dated note 2026-09-27 (single composition, ADR 0030). Earlier (2026-09-26): "Amendment 2026-09-26 (ADR 0026)": §4 input line without m:ss, §5 LimiteIngresso ÷ 2.4 and the deferred items answered
enforced_by:   # migrated 2026-09-26 (mismAgent 0.22) from the legacy inline shell rule: same grep/find logic, now versioned checks run by the gate (architettura-test ControlliAdrTest, red-green on fixture/<check>/)
  - check: architettura-test/controlli-adr/adr-0021-confini-sintesi.sh
  # legacy note: green on the tree today (vacuous: no sintesi/ yet); validated 2026-09-25 via bash -c — tree exit 0; fixtures: PASS with `ParlanteId` only in `//`/KDoc lines, with `ParlanteIdentita`, with a `snastro.parlanti.applicazione` import in sintesi/adattatori, with `riassuntoQueries` in sintesi/; FAIL on `import snastro.kernel.ParlanteId` in sintesi/applicazione, on a fully-qualified `snastro.parlanti.…` use in sintesi/dominio, on `segmentoQueries` in sintesi/adattatori, on `riassuntoFonteQueries` in trascrizione/
---
# 0021 — Sintesi: modules, in-process boundaries and ports

## Context
Feature `sintesi` adds the bounded context `Sintesi` (context-map, 2026-09-25): a `Riassunto` of one
`Registrazione`, made by a local LLM, with `Fonte`s checked against the `Trascritto`
(`features/sintesi/tactical-model.md`, user-confirmed). The trunk is fixed: hexagonal modular
monolith, one module set per context, in-process boundaries only (ADR 0002). This ADR adds the
Sintesi modules and edges, and pins each boundary as a consumer-owned port with its contract test.
It decides nothing about the LLM runtime: spike `runtime-llm-in-app` is still open (see §5).

## Decision

### 1. Modules (amendment of `architecture.md` § Module map)
| Gradle module | Package | Contains |
|---|---|---|
| `:sintesi:dominio` | `snastro.sintesi.dominio` | `Riassunto` root (+ `RiassuntoId`, `StatoRiassunto`, `Argomento`, `Sommario`, `TestoConVoci` with its storage codec, `Decisione`, `QuestioneAperta`, `Azione`, `PuntoChiave`, `Fonte`, `StrutturaTrascritto`, the `Verifica delle fonti`); `LunghezzaMassimaRiassunto` root (+ word-count VO); the pure guard `Riassumibilita` ([INV-S6], shared by the command and the view); the pure input builder and its estimate (`IngressoRiassunto`, `LimiteIngresso`); events; `ErroreSintesi` in `ErroriSintesi.kt` |
| `:sintesi:applicazione` | `snastro.sintesi.applicazione` | `comandi`: `Riassumi`, `EseguiProssimoRiassunto`, `RecuperaRiassuntiInterrotti`, `ModificaLunghezzaMassimaRiassunto` · `politiche`: sostituzione-trascritto, eliminazione-registrazione · `letture`: `riassunto-vista`, `impostazioni-sintesi`, `RiassuntiInAttesa` (the queue listing, ADR 0023) · `porte`: `RiassuntoRepository`, `LunghezzaMassimaRiassuntoRepository`, `LettoreTrascritto`, `LettoreNomi`, `ModelloLinguistico`, `DisponibilitaModelloLinguistico`, `ErroreApplicazioneSintesi` · `eventi`: published events |
| `:sintesi:adattatori` | `snastro.sintesi.adattatori` | `persistenza`: SQLDelight repositories (ADR 0022) · `porte`: `LettoreTrascrittoDaTrascrizione`, `LettoreNomiDaParlanti` · `eventi`: `AbbonatoTrascrizioneSintesi`, `AbbonatoProgettoSintesi` (synchronous) · `ml`: `ModelloLinguistico` adapter over `:llm` |
| `:llm` | `snastro.llm` | the local LLM runtime (technical, like `:ml-sherpa`): loading, generation, cancellation, release. **Its content is decided by spike `runtime-llm-in-app`'s ADR** (§5). Created by the adapter block the spike unblocks, not before. |

`RiassuntoId` lives in `:sintesi:dominio`, not in `:kernel`: no other context uses it, and the
`:avvio` queue speaks primitive ids (ADR 0023).

### 2. Allowed edges (amendment of `architecture.md` § Allowed dependency edges + `verificaDipendenzeModuli`)
| From | May depend on |
|---|---|
| `:sintesi:dominio` | `:kernel` |
| `:sintesi:applicazione` | `:sintesi:dominio`, `:kernel` |
| `:sintesi:adattatori` | `:sintesi:applicazione`, `:sintesi:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione` (the published `RegistrazioneEliminata` only), `:trascrizione:applicazione`, `:parlanti:applicazione`, `:llm` |
| `:llm` | `:kernel`, `:modelli` (the installed model's path, like `:ml-sherpa`) |
| `:ui` | *(adds)* `:sintesi:applicazione` |

`:avvio` and `:architettura-test` already reach every module. The path-holder project `:sintesi`
gets an empty row, like `:progetto`. **No context depends on Sintesi.** `Documento` stays
*separate ways* (context-map). `:sintesi:*` has no edge to `:modelli`. The model's availability is
read through a port implemented in `:avvio`, and the download goes through `:ui`'s
`ServizioModelli` (ADR 0025).

### 3. Boundaries (all in-process: consumer-owned port + consumer-driven contract test)
Published Language: kernel VOs (`RegistrazioneId`, `SegmentoId`, `VoceId`, `VoceRef`, `IntervalloMs`,
`ProgettoId`) + primitives. Each port has `<Porta>Contratto` (abstract) + `<Porta>Finta` in
`:sintesi:applicazione`'s `testFixtures`. The fake runs the contract in the gate (D1), and the real
adapter runs the same contract (D2) (dev-architecture `#porta-contratto`).

| Boundary | Consumer port (`:sintesi:applicazione ..porte`) | Supplier | Authorship | Pinned shape |
|---|---|---|---|---|
| Trascrizione → Sintesi (read) | `LettoreTrascritto` (Sintesi's own; same name as Documento's, another package) | `VociDelTrascritto.segmenti` + `StatiElaborazione` (existing public queries of `:trascrizione:applicazione`) | read → consumer-driven | `segmenti(registrazioneId): List<SegmentoSintesi>?`. `null` means no `Trascritto`. `SegmentoSintesi(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String)`, in the order `VociDelTrascritto.segmenti` gives ([INV-7]). Also `elaborazioneAperta(registrazioneId): Boolean`, true iff the latest `Elaborazione` is `in_attesa \| in_corso`. |
| Parlanti → Sintesi (read) | `LettoreNomi` (Sintesi's own) | `NomiDelleVoci.nomi` | read → consumer-driven (conformist) | `nomi(registrazioneId): Map<VoceRef, String>` (attributed `Voce`s only, current `Nome`, an `eliminato` still resolves). A missing key is rendered by Sintesi as "Voce n". **Read at run time (to label the input) and at display. Never stored** ([INV-S5]). |
| Trascrizione → Sintesi (event) | `AbbonatoTrascrizioneSintesi` (`:sintesi:adattatori ..eventi`, **synchronous**) | published `TrascrittoSostituito(registrazioneId)` (ADR 0018) | producer-driven | → sostituzione-trascritto policy **inside the completion transaction** of the re-run (§6) |
| Progetto → Sintesi (event) | `AbbonatoProgettoSintesi` (**synchronous**) | published `RegistrazioneEliminata(registrazioneId, …)` (ADR 0020) | producer-driven | → eliminazione-registrazione policy **inside the deleting transaction** (ADR 0024) |
| `:modelli` → Sintesi (read, technical) | `DisponibilitaModelloLinguistico` | `:avvio` over its `ServizioModelli` state holder + `ProvisioningModelli` (ADR 0025) | read → consumer-driven | `stato(): StatoModelloLinguistico` = `NonInstallato(dimensioneByte: Long)` \| `InDownload(scaricatiByte: Long, totaliByte: Long)` \| `DownloadFallito(motivo: MotivoDownload)` \| `Installato`. The [INV-S6] guard needs only `Installato`. `riassunto-vista` needs all four. |
| LLM (technical) | `ModelloLinguistico` | `:sintesi:adattatori ..ml` over `:llm` (§5) | port owned by Sintesi | §4 |
| Sintesi → UI (read-models) | — | `riassunto-vista`, `impostazioni-sintesi` (`:sintesi:applicazione ..letture`) | consumer-driven (UI) | `features/sintesi/UI/ux-proposal.md` § Data views, with one arbitration: the queue position is **not** in `riassunto-vista` (ADR 0023 §4) |
| Sintesi → `:avvio` (queue listing) | — | `RiassuntiInAttesa.elenco(): List<RiassuntoInCoda(riassuntoId: String, registrazioneId, richiestoAlle: Instant)>` in FIFO order | consumer-driven (queue owner) | ADR 0023 |

**Write side (producer-driven):** `Riassumi(registrazioneId, argomento: String?)`,
`ModificaLunghezzaMassimaRiassunto(progettoId, parole: Int)`,
`EseguiProssimoRiassunto(esclusi: Set<String>, primaDi: Instant?)` (ADR 0023),
`RecuperaRiassuntiInterrotti`. Each returns `Esito` (CR-16).

**Published events** (`snastro.sintesi.applicazione.eventi`, delivered **after commit only**, no
synchronous consumer): `RiassuntoRichiesto(registrazioneId)`, `RiassuntoAvviato(registrazioneId)`,
`RiassuntoPronto(registrazioneId)`, `RiassuntoFallito(registrazioneId, motivo)`,
`RiassuntoEliminato(registrazioneId)`, `LunghezzaMassimaRiassuntoModificata(progettoId)`. The
consumers are all in `:avvio`: view refresh (`Cambiamento(registrazioneId)`), the queue signal and
the best-effort cancellation (ADR 0023).

### 4. `ModelloLinguistico`: runtime-neutral, structured, cancellable
```kotlin
public interface ModelloLinguistico {
    /** Blocking. Never called inside a UnitaDiLavoro transaction (ADR 0012, ADR 0023 §5). */
    public fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello>
}
public data class RichiestaRiassunto(
    val ingresso: String,               // the labelled input built by IngressoRiassunto (pure, Sintesi-owned)
    val argomento: String?,             // already validated by the Argomento VO
    val lunghezzaMassimaParole: Int,    // the cap fixed on the Riassunto ([INV-S10])
)
public data class RispostaModello(      // raw, UNVERIFIED — the root applies [INV-S4] to it
    val sommario: String?,
    val decisioni: List<ElementoRisposta>, val questioniAperte: List<ElementoRisposta>,
    val azioni: List<AzioneRisposta>, val puntiChiave: List<PuntoChiaveRisposta>,
)
public data class ElementoRisposta(val testo: String, val fonti: List<Int>)                    // fonti = segmentoId numbers
public data class AzioneRisposta(val testo: String, val fonti: List<Int>, val responsabile: Int?)  // voceId number
public data class PuntoChiaveRisposta(val testo: String, val fonti: List<Int>, val parlante: Int?)
```
- **Who owns what.** Sintesi owns the **input format** and the **reference syntax**:
  - the input line is `[s<segmentoId> V<voceId> m:ss] <testo>`, plus a legend `V<n> = <Nome | Voce n>`
    *(amended 2026-10-01, [ADR 0032](0032-riassunto-senza-nomi-nell-ingresso.md): the legend is always `V<n> = Voce n`, never a Nome)*;
    *(amended 2026-09-26, ADR 0026: `[s<segmentoId> V<voceId>] <testo>`, no `m:ss` — see the Amendment below)*
  - in every answer text, a speaker is written `{V<n>}` (literal braces doubled). This is the port's
    canonical form. The adapter translates whatever syntax its prompt uses into it, so the root and
    storage (ADR 0022) never depend on a prompt.
  
  The **adapter** owns the instruction prompt and the runtime's constraint format (JSON schema or
  grammar). That is **answer schema v1**, provisional until spike `qualita-riassunto`'s ADR, and
  the wording of the `Argomento` instruction, provisional until `filtro-fuori-tema`.
- **Errors** (`ErroreApplicazioneSintesi`, technical, `..porte`): `ModelloNonDisponibile`,
  `IngressoTroppoLungo(token: Int)`, `ErroreRuntime(motivo: String)`, `RispostaNonValida`, `Annullato`.
  `EseguiProssimoRiassunto` maps each one to the `Riassunto`'s failure reason:
  - `ModelloNonDisponibile` → `modello_non_disponibile`;
  - `IngressoTroppoLungo` → `troppo_lunga` (the exact-count backstop of [INV-S6]);
  - `ErroreRuntime` and `RispostaNonValida` → `errore_modello`;
  - `Annullato` → nothing is written. A cancelled run belongs to a deleted `Riassunto` ([INV-S8]).
- **Contract** (`ModelloLinguisticoContratto`), run against the fake in the gate and against the
  real adapter under `@Tag("modelli")`:
  - the answer is structurally complete (all five lists present, possibly empty);
  - no answer text contains a speaker in any form other than `{V<n>}`;
  - once `annullato()` becomes true, or the thread is interrupted, the call returns
    `Errore(Annullato)` within a bounded time. The spike states the bound.
  - The contract does **not** assert that the `Fonte`s are valid. That is the root's job ([INV-S4]):
    the fake can return invalid ids on purpose, so the Verifica tests have input.
- **Test guard** (the ADR 0012 (b) pattern): `ModelloLinguisticoFinto` **throws if it is invoked while
  the fake `UnitaDiLavoro` has a transaction open**. Every AC-test of `esegui-riassunto` then fails
  on a regression that moves the call into a transaction.

### 5. Deferred to spike `runtime-llm-in-app` (its closing ADR, numbered after this feature's)
*(answered 2026-09-26 by [ADR 0026](0026-runtime-llm-jni-llama.md): JNI in-process; see the Amendment below)*
This ADR fixes **only** the port and the module. The spike's ADR decides:
- **JNI (llama.cpp in-process) or `llama-server` sidecar (loopback).**
  - JNI → ADR 0004's `enforced_by` (`System.load` only in `ml-sherpa`) must admit `./llm/`.
  - Sidecar → ADR 0008's network rule must admit a **loopback-only** client in `./llm/`, bound to
    `127.0.0.1`, with process lifecycle and cleanup.
  
  The spike ADR amends exactly one of the two, and nothing else of them.
- Native coordinates and checksums, packaged per ADR 0016. Runtime binaries are fetched at **build**
  time, never downloaded by the running app.
- Metal/GPU use.
- Keep-warm vs unload after each run. The default is unload: the queue is shared with the sherpa
  pipeline (ADR 0023).
- The measured cancellation bound.
- The calibration of `LimiteIngresso` and of the [INV-S9] upper bound.
  - **Provisional `LimiteIngresso`, until then (its single home):**
    estimated tokens = ⌈characters of the labelled input ÷ 3⌉, compared with a limit of **28 000**.
    The ratio is deliberately conservative: the model spike measured ≈ 3.4 characters per token on
    Via Roquel, so the estimate refuses early rather than late.
  - The constants live only in `LimiteIngresso` (`:sintesi:dominio`).
  - The exact token count of the runtime stays the run-time backstop
    (`IngressoTroppoLungo` → `troppo_lunga`).
- The `:modelli` catalogue entry values (ADR 0025).

No first-release block other than the real `ModelloLinguistico` adapter (and `:llm`) waits on it.
The fake keeps every other block buildable and the gate headless.

### 6. Subscribers and policies
- **`TrascrittoSostituito` → sostituzione-trascritto policy** (synchronous, in the re-run's completion
  transaction, after ADR 0018 §2 has already saved the new `Trascritto`, which this policy reads):
  - remove every `Riassunto` of the `Registrazione` (any state);
  - if at least one existed, and the new `Trascritto` passes `Riassumibilita` restricted to
    {model `Installato`, input within the limit}, create one `in_attesa` `Riassunto` with:
    - the `Argomento` of the most recent removed one (by `richiestoAlle`);
    - the `Progetto`'s current lunghezza massima ([INV-S10]);
    - `richiestoAlle` = now (injected `Clock`);
  - publish `RiassuntoEliminato` (and `RiassuntoRichiesto` if it created one).
  
  The policy never returns `Errore` for a refused re-summary: it creates nothing. Only an infra
  fault dooms the completion, which ADR 0018 §6 compensates to `fallita`.
- **`RegistrazioneEliminata` → eliminazione-registrazione policy**: ADR 0024.
- Both are **structural** (no LLM call, no file I/O): they live in `..politiche` and never reference
  `ModelloLinguistico` (review criterion + the fake's transaction guard).

### 7. [INV-S5] mechanically: no Parlanti identity in Sintesi
- `:sintesi:dominio` and `:sintesi:applicazione` never reference `ParlanteId` (a kernel type, so
  the module graph alone cannot stop it) nor any `snastro.parlanti.` type. The edges table already
  forbids the import. The grep also catches fully-qualified uses. → `enforced_by`, clause 1.
- Storage: no `nome`/`parlante_id` column in Sintesi's schema → ADR 0022's `enforced_by`.
- Names cross only as `Map<VoceRef, String>` from `LettoreNomi`, into the labelled input's legend and
  into `riassunto-vista`. The root never receives a `Nome`. The answer is re-expressed as `{V<n>}`
  by the adapter.
- **Known blind spot → review + test:** a literal name written by the model into free text (e.g. "Marco
  dice…") cannot be told apart from ordinary words. Spike `qualita-riassunto` measures how often it
  happens. The prompt forbids it. It is not an [INV-S4] drop rule (the tactical model defines no such
  rule).

### 8. Table ownership, both directions
Sintesi reads other contexts only through their `applicazione` APIs and never touches their
generated queries. No other context touches Sintesi's (`riassunto*`, `impostazioniSintesi`).
→ `enforced_by`, clauses 2 and 3 (the ADR 0018 / ADR 0020 pattern).

### 9. Physical deletions (amendment of dev-architecture `#aggregato`)
The list of documented repository `rimuovi`s gains the `Riassunto`:
- a previous `pronto` replaced on completion ([INV-S3]);
- a previous `fallito` removed by `Riassumi` ([INV-S3]);
- every `Riassunto` of a `Registrazione` on `TrascrittoSostituito` / `RegistrazioneEliminata` ([INV-S8]).

The root has no deletion method. `LunghezzaMassimaRiassunto` is never deleted.

### 10. Release composition
The feature's wiring is a new composition **R3** (`snastro.avvio.r3`, block `avvio-sintesi`,
built on R2 because `LettoreNomi` needs Parlanti). It registers both synchronous subscribers
**before** the first command. It wires the shared queue (ADR 0023), `DisponibilitaModelloLinguistico`
and the Riassunto tab. R0–R2 compositions create no `Riassunto` and show no tab.

*(2026-09-27, [ADR 0030](0030-composizione-unica-per-contesto.md) [user])* R0–R2 are retired as compositions. Sintesi is
now `ModuloSintesi` of the single composition (`snastro.avvio.sintesi`), and its two synchronous subscribers head the
declared list (Sintesi → Parlanti → Trascrizione) that `apriProgetto` registers before the queue and before any
command. "R0–R2 compositions create no `Riassunto`" no longer applies: no such composition exists.

## Rejected options
- **Reusing Documento's ports / a shared "transcript reader".** Ports are consumer-owned (ISP).
  Sharing one would couple two consumers that are *separate ways*.
- **Storing the `Nome` (or `ParlanteId`) in the `Riassunto` to spare a read.** A rename would then
  make the `Riassunto` wrong (user decision, [INV-S5]).
- **Returning raw JSON from the port.** The prompt/schema format would leak into the root, and a
  runtime swap would touch the domain.
- **`RiassuntoId` in `:kernel`.** It has no second user.
- **Letting `:sintesi:adattatori` depend on `:modelli`.** The download *progress* lives in `:avvio`'s
  state holder (ADR 0008 R10), so the adapter would see only half of the state.

## Consequences
- Amended in place, with dated pointers to this ADR:
  - `architecture.md` (modules, edges, boundaries);
  - `build.gradle.kts` `allowedModuleEdges` + `settings.gradle.kts`, applied by the scaffold block of the feature;
  - ADR 0006 `enforced_by` (deny-list gains `llm`);
  - ADR 0018 §5 (new synchronous consumer of `TrascrittoSostituito`);
  - `code-rules.md` CR-3 (SQLDelight also in `:sintesi:adattatori`) and CR-10 (Sintesi "Not:" synonyms);
  - `dev-architecture-app.md` (deletion list).
- Konsist (`:architettura-test`) is extended by the scaffold block with:
  - the `sintesi` context in the CR-1 package rules;
  - the Sintesi "Not:" synonyms (`summary`, `verbale`, `report`, `resoconto`, `minuta`, …, from `context-map.md`);
  - `:llm` in CR-3's technical-confinement list.
- Enforcement:
  - `enforced_by` above (prohibition, 3 clauses), and ADR 0022's (storage).
  - Test: the `ModelloLinguisticoFinto` transaction guard.
  - Discursive (code review): policies never call `ModelloLinguistico`; names only through
    `LettoreNomi`; the adapter re-expresses every speaker as `{V<n>}`.

## Amendment 2026-09-26 — the runtime spike's answers ([ADR 0026](0026-runtime-llm-jni-llama.md)) [user]
Spike `runtime-llm-in-app` closed. ADR 0026 is the single home of the runtime; this ADR changes only where §4
and §5 fixed provisional values:
- **§4, input line (user decision):** each `Segmento` becomes **`[s<segmentoId> V<voceId>] <testo>`**. The `m:ss`
  timestamp is dropped: it cost 21.3 % of the input tokens (Qwen splits digits one token each), and nothing in the
  answer uses it (a `Fonte` is a `segmentoId`; its minute is resolved at display). The legend
  `V<n> = <Nome | Voce n>` and the `{V<n>}` reference syntax are unchanged.
- **§4, cancellation bound:** `ModelloLinguisticoContratto` asserts `Errore(Annullato)` within **10 s**, native
  release included (ADR 0026 §4).
- **§5, `LimiteIngresso` (calibrated, still its single home in `:sintesi:dominio`):** estimated tokens =
  **⌈characters ÷ 2.4⌉** (integer form ⌈5 × characters ÷ 12⌉), limit **28 000** unchanged. The provisional ÷ 3
  under-estimated by ≈ 17 % (measured 2.51 characters/token). Consistent with the runtime's `n_ctx` = 40 960
  (ADR 0026 §5). The exact count stays the run-time backstop (`IngressoTroppoLungo` → `troppo_lunga`).
- **§5, [INV-S9]:** the upper bound stays **2500 words** (default 2000); the context is sized for it (ADR 0026 §5).
- **§5, the other deferred items:** JNI (ADR 0004 amended, ADR 0008 untouched), natives, Metal, unload after each
  run, the catalogue values: ADR 0026 §1–§8.

## Amendment 2026-09-26 (b) — `:llm` is replaced by the separate library `:llama-jni` ([ADR 0027](0027-libreria-llama-jni-separata.md)) [user]
- **§1 and §2:** the `:llm` row (`snastro.llm`, edges `:kernel`, `:modelli`) is withdrawn and never created. The
  runtime is `:llama-jni` (`./llama-jni/`, package `io.github.lucolucus.llamajni`), with **no** project
  dependency.
- **Edges:** `:sintesi:adattatori` → `:llama-jni` replaces → `:llm`.
- **The installed model's path** (the reason for the old `:llm → :modelli` edge) is resolved by `:avvio` from
  `:modelli` and handed to the `..ml` adapter. `:sintesi:*` still has no edge to `:modelli`.
- **Unchanged:** §4's port, its contract and everything else here.


## Amendment 2026-10-01 — the `Riassunto` of an `Incontro` ([ADR 0033](0033-incontro-progetto-chiavi-confini.md) §4, [ADR 0037](0037-riassunto-dell-incontro.md)) [user D-0001, D-0004, D-0007]
- §3: every Sintesi port, command, event and the queue listing is keyed by `incontroId`; a new port `LettoreIncontro`
  (Progetto → Sintesi); `LettoreTrascritto.elaborazioneAperta` becomes `statoParte`. Shapes: ADR 0033 §4.
- §4: the input line is `[s<k> V<n>] <testo>` with k the 1-based position in the one-pass input; `fonti` are those labels.
- §6: **the sostituzione-trascritto policy and `AbbonatoTrascrizioneSintesi` are removed**; no automatic "Riassumi" exists.
- §9: a `Riassunto` is deleted with the `Incontro` (its last `Parte`), never on `TrascrittoSostituito`.
- This ADR's check is unchanged; ADR 0037 adds the prohibition on `TrascrittoSostituito` in Sintesi.
