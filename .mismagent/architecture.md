# snastro — Architecture (project definition file)

> Written by the architect (model movement, foundational dispatch, 2026-09-23) after the user's
> deliberation. Change it only through a new deliberation + an ADR (`supersedes:`).
> The wave-0 scaffold derives the skeleton from this file; the gate's dependency lint
> (`verificaDipendenzeModuli` + Konsist in `:architettura-test`) is its executable projection.
> Rationale: `decisions/0001`–`0012`. Code-writing rules: `code-rules.md`.

## Style
**Hexagonal (ports & adapters) modular monolith, one Gradle module set per bounded context**
([ADR 0002](decisions/0002-esagonale-modulo-per-contesto.md)). Kotlin/JVM, Compose Multiplatform
Desktop, single side `app` ([ADR 0001](decisions/0001-stack-kotlin-compose-desktop.md)). Every
context boundary is **in-process**: a consumer-owned port + an in-process consumer-driven contract
test. No OpenAPI, no IPC.

## Module map
Directory = Gradle project path (`progetto/dominio` ↔ `:progetto:dominio`). Kotlin sources in
`<module>/src/main/kotlin/`, tests in `<module>/src/test/kotlin/`. Group/package root `snastro`.

| Gradle module | Package | Contains |
|---|---|---|
| `:kernel` | `snastro.kernel` | shared kernel: ids (`@JvmInline value class`: `ProgettoId`, `RegistrazioneId`, `ElaborazioneId`, `VoceId`, `SegmentoId`, `ParlanteId`), `VoceRef`, `IntervalloMs`, `RiferimentoAudio`, `CampioniAudio`, `EstrattoRef`, `Esito`, `ErroreDominio` base, `EventoDominio`, `EventoPubblicato`, `Creato`, `RicostituzioneDaPersistenza`, ports `GeneratoreId`, `UnitaDiLavoro`, `LetturaCoerente` *(2026-09-27, [ADR 0029](decisions/0029-lettura-coerente-deferred.md))*, `DispatcherEventi`; the `Esito` helpers (`valoreOppureErrore`, `ignoraEsito`) *(2026-09-27, shared vocabulary)*. No library dependency, no coroutines *(2026-09-27, [ADR 0028](decisions/0028-librerie-tecniche-supporto.md))* *(amended 2026-09-23, R9)* |
| `:progetto:dominio` | `snastro.progetto.dominio` | `Progetto`, `Registrazione` aggregates, VOs, events |
| `:progetto:applicazione` | `snastro.progetto.applicazione` | commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`, *(2026-09-25, ADR 0020)* `EliminaRegistrazione`, `CompletaEliminazioniRegistrazioni`), queries/read-models, repository ports, `SondaAudio` port, public query API (`CatalogoRegistrazioni`) |
| `:progetto:adattatori` | `snastro.progetto.adattatori` | SQLDelight repositories, file copy of sources, `SondaAudio` adapter (→ `:audio`) |
| `:trascrizione:dominio` | `snastro.trascrizione.dominio` | `Elaborazione`, `Trascritto` (`Voce`, `Segmento`, `Revisione` methods), events |
| `:trascrizione:applicazione` | `snastro.trascrizione.applicazione` | commands (`AvviaElaborazione`, `UnisciVoci`, `DividiVoce`, `RiassegnaSegmento`), read-models, ports (repository; `LettoreRegistrazione` → Progetto; ML: `DecodificatoreAudio`, `Diarizzatore`, `RiconoscitoreParlato`, `Vad`, `Allineatore`, `SegnalatoreFase`), public query API (`VociDelTrascritto`) |
| `:trascrizione:adattatori` | `snastro.trascrizione.adattatori` | repositories, port adapters (→ Progetto API, → `:audio`, → `:ml-sherpa`), pure `Allineatore` |
| `:parlanti:dominio` | `snastro.parlanti.dominio` | `Parlante` (+ `ImprontaVocale`), `Attribuzione`, events |
| `:parlanti:applicazione` | `snastro.parlanti.applicazione` | commands (`ConfermaAttribuzione`, `SaltaVoce`, `RinominaParlante`, `PromuoviParlante`, `EliminaParlante`), revisione-policy, read-models (`Proposta`, `EstrattoAudio`, `PropostaUnione`, `ParlantiDelProgetto`, `ParlantiAttivi`), ports (repository; `LettoreVoci` → Trascrizione; `LettoreRegistrazione` → Progetto; `EstrattoreImpronta`, `ConfrontoImpronte`), public query API (`NomiDelleVoci`) |
| `:parlanti:adattatori` | `snastro.parlanti.adattatori` | repositories, port adapters (→ Trascrizione / Progetto API, → `:ml-sherpa`), pure `ConfrontoImpronte` |
| `:sbobinatura:applicazione` | `snastro.sbobinatura.applicazione` | `Sbobinatura` projection (pure: inputs → markdown string), `Rigenerazione` policy, ports (`LettoreTrascritto`, `LettoreNomi`, `ScrittoreSbobinatura`) |
| `:sbobinatura:adattatori` | `snastro.sbobinatura.adattatori` | port adapters (→ Trascrizione / Parlanti API), atomic `.md` writer |
| `:sintesi:dominio` | `snastro.sintesi.dominio` | *(2026-09-25, [ADR 0021](decisions/0021-sintesi-moduli-confini-porte.md))* `Riassunto` (+ `Fonte`, elements, `StrutturaTrascritto`, `Verifica delle fonti`), `LunghezzaMassimaRiassunto`, pure guard `Riassumibilita`, input builder `IngressoRiassunto`, events, `ErroreSintesi` |
| `:sintesi:applicazione` | `snastro.sintesi.applicazione` | commands (`Riassumi`, `EseguiProssimoRiassunto`, `RecuperaRiassuntiInterrotti`, `ModificaLunghezzaMassimaRiassunto`), policies (on `TrascrittoSostituito` / `RegistrazioneEliminata`), read-models (`riassunto-vista`, `impostazioni-sintesi`, `RiassuntiInAttesa`), ports (repositories; `LettoreTrascritto` → Trascrizione; `LettoreNomi` → Parlanti; `ModelloLinguistico`; `DisponibilitaModelloLinguistico`) |
| `:sintesi:adattatori` | `snastro.sintesi.adattatori` | SQLDelight repositories (`6.sqm`, [ADR 0022](decisions/0022-persistenza-sintesi-6sqm.md)), port adapters (→ Trascrizione / Parlanti API), synchronous subscribers, `ModelloLinguistico` adapter (→ `:llama-jni`, *2026-09-26, [ADR 0027](decisions/0027-libreria-llama-jni-separata.md)*) |
| `:persistenza` | `snastro.persistenza` | SQLDelight schema `.sq`, migrations `.sqm` (source of the schema, ADR 0006 (a)), driver factory (WAL, FK, `secure_delete`), `UnitaDiLavoro` impl |
| `:audio` | `snastro.audio` | bytedeco FFmpeg `sonda`/`decodifica` → derived WAV; javax.sound player `RiproduttoreWav` (adapted to `:ui`'s `LettoreAudio` by `:avvio`) |
| `:ml-sherpa` | `snastro.ml` | native-lib loading, sherpa session config, `AutoCloseable` wrappers, diarization/ASR/VAD/embedding engines |
| `:llama-jni` | `io.github.lucolucus.llamajni` | *(2026-09-26, [ADR 0027](decisions/0027-libreria-llama-jni-separata.md) [user]; replaces `:llm` of ADR 0021/0026, never created)* **separate, shareable library, no snastro dependency** (no project edge, no `snastro.*` in code or build — ADR 0027's check): llama.cpp b11195 via our JNI shim (`src/main/c/`, compiled outside the gate, per OS: macOS arm64 Metal, Windows x64 / Linux x64 Vulkan + CPU), every native load, neutral English API (load / devices / open model / exact token count / bounded, cancellable generation / close), its own native tasks, README, notices, tests |
| `:modelli` | `snastro.modelli` | model catalogue (URL, SHA-256, licence), first-run download, cache paths — the ONLY network module |
| `:ui` | `snastro.ui` | Compose screens S1–S4 + shared `lettore-audio`: presenters (state holders, unit-tested) + thin composables; declares `LettoreAudio` |
| `:supporto` | `snastro.supporto` | *(2026-09-27, [ADR 0028](decisions/0028-librerie-tecniche-supporto.md) [user])* **domain-free technical library**: `RitentaConBackoff`, `gestoreErroriNonCatturati`, `figlioDi`, `catturaNonFatale`, `Segnalazione` (the pinned public API, CR-18c). Only `kotlinx-coroutines-core`; no snastro dependency |
| `:avvio` | `snastro.avvio.{progetto, trascrizione, parlanti, sintesi, sbobinatura, modelli, coda, smoke}` | `main()`, the **single** composition root (*2026-09-27, [ADR 0030](decisions/0030-composizione-unica-per-contesto.md)*: `PorteProgetto` once per project, one `ModuloComposizione` per context, `apriProgetto` with declared orders; no per-release composition), adapter selection (config), the shared queue (`avvio.coda`), startup policies, `--smoke` mode |
| `:architettura-test` | `snastro.architettura` | Konsist rules (test-only module) |
| `:supporto-test` | `snastro.supporto.test` | *(2026-09-27, [ADR 0028](decisions/0028-librerie-tecniche-supporto.md) [user])* test-only helpers, **never shipped**: `attendiFinche`, `OrologioFinto`, `conScopeDiProva`. No snastro dependency |

## Allowed dependency edges (project → project) — the dependency lint's source
Anything not listed is forbidden (`verificaDipendenzeModuli` fails the build).

| From | May depend on |
|---|---|
| `:kernel` | — |
| `:<ctx>:dominio` | `:kernel` |
| `:<ctx>:applicazione` | `:<ctx>:dominio`, `:kernel` |
| `:progetto:adattatori` | `:progetto:applicazione`, `:progetto:dominio`, `:kernel`, `:persistenza`, `:audio`, `:supporto` |
| `:trascrizione:adattatori` | `:trascrizione:applicazione`, `:trascrizione:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione`, `:audio`, `:ml-sherpa`, `:supporto` |
| `:parlanti:adattatori` | `:parlanti:applicazione`, `:parlanti:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione`, `:trascrizione:applicazione`, `:audio`, `:ml-sherpa`, `:supporto` |
| `:sbobinatura:applicazione` | `:kernel` |
| `:sbobinatura:adattatori` | `:sbobinatura:applicazione`, `:kernel`, `:trascrizione:applicazione`, `:parlanti:applicazione`, `:progetto:applicazione`, `:supporto` |
| `:sintesi:adattatori` | `:sintesi:applicazione`, `:sintesi:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione`, `:trascrizione:applicazione`, `:parlanti:applicazione`, `:llama-jni` *(ADR 0021; library per ADR 0027)*, `:supporto` |
| `:llama-jni` | — *(no project dependency, ADR 0027; `:llm → :kernel, :modelli` of ADR 0021 withdrawn)* |
| `:persistenza` | `:kernel` |
| `:audio` | `:kernel` |
| `:ml-sherpa` | `:kernel`, `:modelli` |
| `:modelli` | `:kernel` |
| `:ui` | `:kernel`, every `:<ctx>:applicazione`, `:supporto` |
| `:supporto` | — *(ADR 0028: no snastro dependency, not even `:kernel`)* |
| `:supporto-test` | — *(ADR 0028)* |
| `:avvio` | every module (composition root) |
| `:architettura-test` | (test) every module |

**Test-only edge** *(2026-09-27, [ADR 0028](decisions/0028-librerie-tecniche-supporto.md) [user])*:
- Any module except `:llama-jni` may depend on `:supporto-test`, but only in `testImplementation` / `testRuntimeOnly`.
- `testFixtures*` stays forbidden until `:avvio` stops shipping testFixtures (M2/S5).
- `:supporto` is never reachable from `:kernel`, `*:dominio` or `*:applicazione` (CR-2).

Direction summary: `adattatori → applicazione → dominio → kernel`; cross-context only
`consumer:adattatori → supplier:applicazione`; `Progetto` is upstream of all; `Trascrizione` is
upstream of `Parlanti`, `Sbobinatura` and `Sintesi`; `Parlanti` is upstream of `Sbobinatura` and `Sintesi`; no context depends on `Sintesi` (ADR 0021). `ui` sees only
`applicazione`. Technical modules (`persistenza`, `audio`, `ml-sherpa`, `modelli`, and the separate library
`llama-jni`) are reached only from adapters (and `avvio`). The library depends on nothing of snastro (ADR 0027). *(2026-09-27, ADR 0028)* The domain-free `:supporto` is reached only by adapters, `:ui` and `avvio`, and it depends on nothing of snastro. `:supporto-test` is reached only from test source sets.

## Boundaries (feature `sintesi`, 2026-09-25)
Detailed in `features/sintesi/architetture/architecture-overview.md` ([ADR 0021](decisions/0021-sintesi-moduli-confini-porte.md) §3): `LettoreTrascritto`
and `LettoreNomi` (Sintesi's own ports), `ModelloLinguistico` (runtime-neutral; the runtime is llama.cpp via JNI, [ADR 0026](decisions/0026-runtime-llm-jni-llama.md)),
`DisponibilitaModelloLinguistico` (implemented in `:avvio`, [ADR 0025](decisions/0025-modello-facoltativo-su-richiesta.md)), synchronous subscribers to
`TrascrittoSostituito` and `RegistrazioneEliminata`.

## Boundaries (feature `trascrizione-con-parlanti`)
Detailed in `features/trascrizione-con-parlanti/architetture/architecture-overview.md` (ports,
Published Language, authorship, contract tests).

## Pipeline and progress
*(2026-09-25, [ADR 0023](decisions/0023-coda-condivisa-elaborazioni-riassunti.md))* The serial queue is **shared**: `Elaborazione`s and `Riassunto`s
run one at a time in ONE FIFO ordered by request instant, owned by `:avvio` (`CodaCondivisa`). Each context claims its
own head inside its own transaction. The queue position shown by S2 and the Riassunto tab is computed by the owner
(`:ui` port `PosizioniNellaCoda`), never by a context. The LLM never runs inside a transaction and never takes the
sherpa Mutex.

`AvviaElaborazione` (serial queue, single-thread pipeline dispatcher, ADR 0004) runs the stages in
order, reporting each through `SegnalatoreFase`:
`decodifica` (`:audio`) → `diarizzazione` → `trascrizione` → `allineamento`, then commits
`completata` + `Trascritto` in one transaction (ADR 0012).
**`FaseElaborazione` = `decodifica | diarizzazione | trascrizione | allineamento`** (display:
"preparazione audio", "separazione voci", "trascrizione", "allineamento") is **progress information
only**, not guarded state ([INV-3] unchanged). **Pending:** the context-map amendment adding
`FaseElaborazione` to the `Trascrizione` ubiquitous language is owed by the analyst (proposed in
`features/trascrizione-con-parlanti/UI/ux-proposal.md`); until then this file and ADR 0004 are its
reference.

## Transactions and events
One transaction per command; invariant-carrying policies in-transaction; `Sbobinatura`
`Rigenerazione` after commit, idempotent, retried ([ADR 0012](decisions/0012-unita-di-lavoro-ed-eventi.md)).
Cross-context events flow `supplier:applicazione` (published events, Published Language) →
`consumer:adattatori` (subscriber, via the kernel `DispatcherEventi`) → `consumer:applicazione`
(policy). This keeps the edges table above intact (no consumer depends on a supplier's `dominio`).

**Reads** *(2026-09-27, [ADR 0029](decisions/0029-lettura-coerente-deferred.md) [user])*:
- A read runs through the kernel port `LetturaCoerente.inLettura`: `BEGIN DEFERRED` + `query_only`, one snapshot, no write lock.
- Writes stay `BEGIN IMMEDIATE` through `UnitaDiLavoro`.
- One `UnitaDiLavoroSql` per project implements both ports and shares their per-thread state:
  - a read nested in a write joins it;
  - a write nested in a read throws;
  - a failing joined read dooms the write.
- Every multi-table repository read runs in a snapshot, inside the repository.
- SQLDelight transactions are called only in `:persistenza` (CR-3b).

**UI actions that span two contexts (2026-09-24, [ADR 0019](decisions/0019-separazione-semi-automatica.md) §4.1, §5).**
Some user actions need both contexts:
- "Riassegna per somiglianza": a Parlanti plan, then a Trascrizione batch command;
- "Dai un nome a una frase": a Trascrizione move or confirmation, then a Parlanti attribution.

For these, the glue is sequenced in **`:avvio`**, which implements a `:ui`-declared action port in
the per-project scope of ADR 0017 §3. **No context commands another context.** Parlanti still only
reads Trascrizione (a consumer-owned port) and reacts to its events. The glue holds no domain rule:
the plan is a Parlanti read-model, and every invariant is checked by the command that writes. The
edges table is unchanged. *(Amended 2026-09-24 [user], ADR 0019 Amendment (b).2: the glue shows the plan as a
preview and sends the Trascrizione command only on "Applica", with the plan it holds in memory. Holding it is
allowed because it carries ids and intervals only, never an embedding.)*

**Deleting a Registrazione (2026-09-25 [user], [ADR 0020](decisions/0020-elimina-registrazione.md)).**
- `EliminaRegistrazione` (Progetto) publishes `RegistrazioneEliminata` inside its transaction. Trascrizione (a veto if
  an `Elaborazione` is open, then its purge) and Parlanti (purge + INV-25) react as synchronous subscribers, and then
  the `registrazione` row is removed.
- Files (`audio/`, `cache/audio/`, the Sbobinatura `.md`) are removed after commit by their owners.
- A Progetto-owned `eliminazione_in_sospeso` row, written in the same transaction, drives crash recovery at the next
  project open.
- The edges table is unchanged.
- *(2026-09-25, [ADR 0024](decisions/0024-elimina-registrazione-riassunto.md))* A third synchronous subscriber (Sintesi) removes every `Riassunto` of
  the `Registrazione` in any state, and never vetoes. Its IMMEDIATE FK makes a missing subscriber fail the delete.

## Composition (2026-09-27, [ADR 0030](decisions/0030-composizione-unica-per-contesto.md)) [user]
One composition, organised by context rather than by release. The release **method** is unchanged: a new release adds a module and its ACs.
- **`PorteProgetto`**: built once per open project by `SessioneProgettoImpl`. It holds the database, the one `UnitaDiLavoroSql` (`UnitaDiLavoro` + `LetturaCoerente`), the dispatcher, one instance of every SQL repository, `CatalogoRegistrazioni`, the cross-context readers and `LayoutCartellaProgetto`.
- **`ModuloComposizione<Ctx>`**, one each for Trascrizione, Parlanti, Sintesi and Sbobinatura. Each exposes:
  - its synchronous and after-commit subscribers as values (no self-registration in `init`);
  - `fontiCoda()`, `avvia(scope)`/`ferma()`, and typed collaborators for `:ui`.
- **`apriProgetto(porte)`**, in this order:
  1. build the modules;
  2. register the synchronous subscribers from ONE declared list, **Sintesi → Parlanti → Trascrizione**;
  3. register the after-commit subscribers;
  4. `CodaCondivisa(fonti)`, woken by a `Campanello`;
  5. the recoveries;
  6. `avvia`.
- **Shutdown**: `ArrestoProgetto` stops everything in reverse order, with one deadline.
- **`CollaboratoriProgetto`**: typed, non-null fields; no casts.
- **Singletons**: one `Grafo`, one `ContenutoApp`, one `SEZIONI_SHELL`.
- **Import rule**: `snastro.<ctx>.adattatori` is imported only from `avvio.<ctx>` and `avvio.progetto` (ADR 0030 `enforced_by`).

## Enforcement channels (all inside `./gradlew check`)
1. Gradle module graph (compile) + `verificaDipendenzeModuli` (edges table above, plus the test-only edge rule of ADR 0028).
2. Konsist in `:architettura-test` (imports/packages/naming — `code-rules.md`).
3. detekt (style, error handling, `!!`) with `allWarningsAsErrors`.
4. ADR `enforced_by` checks — versioned POSIX `sh` scripts in `architettura-test/controlli-adr/` (`{check, from}` form,
   migrated 2026-09-26), run by `:architettura-test`'s `ControlliAdrTest`: red-green on each check's fixtures, then on the
   tree (a check whose `from` block is not yet integrated is reported, not enforced).

## Amendment 2026-09-23 (build-manifest reconciliation, feature trascrizione-con-parlanti)
- **R9 kernel list** (row above): adds `ElaborazioneId`, `RiferimentoAudio`, `CampioniAudio` (shared by
  two contexts' ports), `EstrattoRef` (`registrazioneId` + a LIST of `IntervalloMs`, played as a
  sequence), `EventoPubblicato`, `Creato`, `RicostituzioneDaPersistenza`, `GeneratoreId`. `Impronta`
  lives in `:parlanti:dominio` (not kernel). Exact signatures: boundary `kernel-pl` in
  `features/trascrizione-con-parlanti/building-blocks.yaml`.
- **R3 project registry:** `:progetto:applicazione` also declares `RegistroProgetti` (per-user list of
  recent projects), implemented in `:progetto:adattatori` over a file in the OS app-data dir. Project
  folder layout, `.lock`, DB opening and registry updates on open/close are done by `:avvio`'s
  `SessioneProgetto`.
- **`:ui` declares ports implemented by `:avvio`:** `LettoreAudio` (existing), `SessioneProgetto`,
  `ApriEsterno` (open file / reveal in folder), `AggiornamentiVista` (refresh flow, R15),
  `ServizioModelli` (S5, R10 — `:ui` cannot reach `:modelli`).
- **R1 read-models per owning context:** a view that mixes contexts is split into one read-model per
  context, joined by `registrazioneId`/`voceId` in the presenter (e.g. S2 = `registrazioni-del-progetto`
  ⨝ `stati-elaborazione` ⨝ `identificazione-registrazioni`). Read-models compute on read over
  repository/query ports (R15); no event-folded tables in v1.
- **R2:** *(superseded 2026-09-23 [user], ADR 0014 / ADR 0012 Amendment (c))* ~~the `RegistrazioneAggiunta` → `AvviaElaborazione` policy lives in `:trascrizione:adattatori`
  (sync subscriber), never in Progetto~~. There is no automatic start: the user starts an `Elaborazione` from S2 with "Trascrivi" or "Riprova", with the optional `NumeroPersone` (1..10) — and, since 2026-09-24 [user], with "Ritrascrivi" on a `completata` one (R2 composition only; the Trascritto is replaced atomically on completion and Parlanti purges the old `VoceRef`s in the same transaction through the synchronous `TrascrittoSostituito` subscriber — ADR 0018). `RegistrazioneAggiunta` has after-commit consumers only. *(amended 2026-09-24 [user], ADR 0018 Amendment (b))* A still-queued (`in_attesa`) `Elaborazione` can be withdrawn from S2 with `AnnullaElaborazione` (the never-started row is deleted, INV-3 unchanged; an `in_corso` one cannot be cancelled; the race with the queue's claim is serialized by `BEGIN IMMEDIATE`).
- **R12:** one mutex serializes every native (sherpa) call, pipeline and `EstrattoreImpronta` alike.
- **R25 (resolved 2026-09-23, ADR 0003 amendment):** `ErroreDominio` is a plain (non-sealed)
  interface — Kotlin forbids sealed subtypes across modules; each context owns one sealed hierarchy
  `Errore<Contesto>` in `Errori<Contesto>.kt`.

## Amendment 2026-09-25 (feature `sintesi`, ADRs 0021–0025)
- **Modules:** `:sintesi:dominio|applicazione|adattatori` and the technical `:llm` (rows above) *(→ the separate library `:llama-jni`, amendment 2026-09-26 below)*. The path-holder
  `:sintesi` gets an empty edge row in `verificaDipendenzeModuli`. The rows follow the `<ctx>` pattern of the edges
  table for `dominio`/`applicazione`.
- **Edges:** see the rows marked ADR 0021. `:ui` gains `:sintesi:applicazione` (covered by "every `:<ctx>:applicazione`").
  `:sintesi:*` has **no** edge to `:modelli`.
- **`:ui` declares ports implemented by `:avvio`** (the existing list gains): `PosizioniNellaCoda` (ADR 0023 §4), and
  `ServizioModelli` gains the optional-model entry and `scaricaFacoltativo(id)` (ADR 0025 §4).
- **R1 read-models per owning context** applies: `riassunto-vista` carries no queue position, and `stati-elaborazione`
  loses `posizioneInCoda`. The presenters join `PosizioniNellaCoda` (ADR 0023 §4).
- **Composition R3** (`snastro.avvio.r3`, block `avvio-sintesi`) wires Sintesi on top of R2 (ADR 0021 §10). *(Superseded 2026-09-27 by § Composition, [ADR 0030](decisions/0030-composizione-unica-per-contesto.md): R0–R3 are no longer separate compositions.)*
- **Schema:** `6.sqm` (6 → 7), Sintesi-owned tables `riassunto`, `riassunto_elemento`, `riassunto_fonte`,
  `impostazioni_sintesi` (ADR 0022).

## Amendment 2026-09-26 (feature `sintesi`, [ADR 0027](decisions/0027-libreria-llama-jni-separata.md)) [user]
- **The LLM runtime is the separate library `:llama-jni`**, directory `./llama-jni/`, package `io.github.lucolucus.llamajni`.
  - It replaces the technical `:llm` of ADR 0021/0026, which is never created.
  - It is a normal subproject of this build, so the gate covers it. Its layout lets it become an included build or its own
    repository with no edits inside it.
  - **Edges:** it has none. `:sintesi:adattatori → :llama-jni`. `:avvio` reaches it for packaging (the copy of
    `assembleNatives`' output into `appResourcesRootDir/<os-arch>/`).
  - **Its build script** applies no `snastro.*` convention plugin and uses no `project(...)`, `rootProject`, `rootDir` or
    `../`.
- **The snastro adapter** (`:sintesi:adattatori ..ml`) keeps the prompt, the schema/GBNF, the parsing, the error mapping, the
  per-run unload and the GPU→CPU retry.
- **Locations come from `:avvio`:** the installed model's path (from `:modelli`) and the native directory
  (`snastro.llm.native.path` / `compose.application.resources.dir`).
- **`verificaDipendenzeModuli`** gains `":llama-jni" to emptySet()` and `:llama-jni` in `:sintesi:adattatori`'s set. It never
  gains an `:llm` row. The library block edits the build files.

