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
| `:kernel` | `snastro.kernel` | shared kernel: ids (`@JvmInline value class`: `ProgettoId`, `RegistrazioneId`, `ElaborazioneId`, `VoceId`, `SegmentoId`, `ParlanteId`), `VoceRef`, `IntervalloMs`, `RiferimentoAudio`, `CampioniAudio`, `EstrattoRef`, `Esito`, `ErroreDominio` base, `EventoDominio`, `EventoPubblicato`, `Creato`, `RicostituzioneDaPersistenza`, ports `GeneratoreId`, `UnitaDiLavoro`, `DispatcherEventi` *(amended 2026-09-23, R9)* |
| `:progetto:dominio` | `snastro.progetto.dominio` | `Progetto`, `Registrazione` aggregates, VOs, events |
| `:progetto:applicazione` | `snastro.progetto.applicazione` | commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`, *(2026-09-25, ADR 0020)* `EliminaRegistrazione`, `CompletaEliminazioniRegistrazioni`), queries/read-models, repository ports, `SondaAudio` port, public query API (`CatalogoRegistrazioni`) |
| `:progetto:adattatori` | `snastro.progetto.adattatori` | SQLDelight repositories, file copy of sources, `SondaAudio` adapter (→ `:audio`) |
| `:trascrizione:dominio` | `snastro.trascrizione.dominio` | `Elaborazione`, `Trascritto` (`Voce`, `Segmento`, `Revisione` methods), events |
| `:trascrizione:applicazione` | `snastro.trascrizione.applicazione` | commands (`AvviaElaborazione`, `UnisciVoci`, `DividiVoce`, `RiassegnaSegmento`), read-models, ports (repository; `LettoreRegistrazione` → Progetto; ML: `DecodificatoreAudio`, `Diarizzatore`, `RiconoscitoreParlato`, `Vad`, `Allineatore`, `SegnalatoreFase`), public query API (`VociDelTrascritto`) |
| `:trascrizione:adattatori` | `snastro.trascrizione.adattatori` | repositories, port adapters (→ Progetto API, → `:audio`, → `:ml-sherpa`), pure `Allineatore` |
| `:parlanti:dominio` | `snastro.parlanti.dominio` | `Parlante` (+ `ImprontaVocale`), `Attribuzione`, events |
| `:parlanti:applicazione` | `snastro.parlanti.applicazione` | commands (`ConfermaAttribuzione`, `SaltaVoce`, `RinominaParlante`, `PromuoviParlante`, `EliminaParlante`), revisione-policy, read-models (`Proposta`, `EstrattoAudio`, `PropostaUnione`, `ParlantiDelProgetto`, `ParlantiAttivi`), ports (repository; `LettoreVoci` → Trascrizione; `LettoreRegistrazione` → Progetto; `EstrattoreImpronta`, `ConfrontoImpronte`), public query API (`NomiDelleVoci`) |
| `:parlanti:adattatori` | `snastro.parlanti.adattatori` | repositories, port adapters (→ Trascrizione / Progetto API, → `:ml-sherpa`), pure `ConfrontoImpronte` |
| `:documento:applicazione` | `snastro.documento.applicazione` | `Documento` projection (pure: inputs → markdown string), `Rigenerazione` policy, ports (`LettoreTrascritto`, `LettoreNomi`, `ScrittoreDocumento`) |
| `:documento:adattatori` | `snastro.documento.adattatori` | port adapters (→ Trascrizione / Parlanti API), atomic `.md` writer |
| `:persistenza` | `snastro.persistenza` | SQLDelight schema `.sq`, migrations `.sqm` (source of the schema, ADR 0006 (a)), driver factory (WAL, FK, `secure_delete`), `UnitaDiLavoro` impl |
| `:audio` | `snastro.audio` | bytedeco FFmpeg `sonda`/`decodifica` → derived WAV; javax.sound player `RiproduttoreWav` (adapted to `:ui`'s `LettoreAudio` by `:avvio`) |
| `:ml-sherpa` | `snastro.ml` | native-lib loading, sherpa session config, `AutoCloseable` wrappers, diarization/ASR/VAD/embedding engines |
| `:modelli` | `snastro.modelli` | model catalogue (URL, SHA-256, licence), first-run download, cache paths — the ONLY network module |
| `:ui` | `snastro.ui` | Compose screens S1–S4 + shared `lettore-audio`: presenters (state holders, unit-tested) + thin composables; declares `LettoreAudio` |
| `:avvio` | `snastro.avvio` | `main()`, composition root, adapter selection (config), serial `Elaborazione` queue + pipeline dispatcher, startup policies, `--smoke` mode |
| `:architettura-test` | `snastro.architettura` | Konsist rules (test-only module) |

## Allowed dependency edges (project → project) — the dependency lint's source
Anything not listed is forbidden (`verificaDipendenzeModuli` fails the build).

| From | May depend on |
|---|---|
| `:kernel` | — |
| `:<ctx>:dominio` | `:kernel` |
| `:<ctx>:applicazione` | `:<ctx>:dominio`, `:kernel` |
| `:progetto:adattatori` | `:progetto:applicazione`, `:progetto:dominio`, `:kernel`, `:persistenza`, `:audio` |
| `:trascrizione:adattatori` | `:trascrizione:applicazione`, `:trascrizione:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione`, `:audio`, `:ml-sherpa` |
| `:parlanti:adattatori` | `:parlanti:applicazione`, `:parlanti:dominio`, `:kernel`, `:persistenza`, `:progetto:applicazione`, `:trascrizione:applicazione`, `:audio`, `:ml-sherpa` |
| `:documento:applicazione` | `:kernel` |
| `:documento:adattatori` | `:documento:applicazione`, `:kernel`, `:trascrizione:applicazione`, `:parlanti:applicazione`, `:progetto:applicazione` |
| `:persistenza` | `:kernel` |
| `:audio` | `:kernel` |
| `:ml-sherpa` | `:kernel`, `:modelli` |
| `:modelli` | `:kernel` |
| `:ui` | `:kernel`, every `:<ctx>:applicazione` |
| `:avvio` | every module (composition root) |
| `:architettura-test` | (test) every module |

Direction summary: `adattatori → applicazione → dominio → kernel`; cross-context only
`consumer:adattatori → supplier:applicazione`; `Progetto` is upstream of all; `Trascrizione` is
upstream of `Parlanti` and `Documento`; `Parlanti` is upstream of `Documento`. `ui` sees only
`applicazione`. Technical modules (`persistenza`, `audio`, `ml-sherpa`, `modelli`) are reached only
from adapters (and `avvio`).

## Boundaries (feature `trascrizione-con-parlanti`)
Detailed in `features/trascrizione-con-parlanti/architetture/architecture-overview.md` (ports,
Published Language, authorship, contract tests).

## Pipeline and progress
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
One transaction per command; invariant-carrying policies in-transaction; `Documento`
`Rigenerazione` after commit, idempotent, retried ([ADR 0012](decisions/0012-unita-di-lavoro-ed-eventi.md)).
Cross-context events flow `supplier:applicazione` (published events, Published Language) →
`consumer:adattatori` (subscriber, via the kernel `DispatcherEventi`) → `consumer:applicazione`
(policy). This keeps the edges table above intact (no consumer depends on a supplier's `dominio`).

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
- Files (`audio/`, `cache/audio/`, the Documento `.md`) are removed after commit by their owners.
- A Progetto-owned `eliminazione_in_sospeso` row, written in the same transaction, drives crash recovery at the next
  project open.
- The edges table is unchanged.

## Enforcement channels (all inside `./gradlew check`)
1. Gradle module graph (compile) + `verificaDipendenzeModuli` (edges table above).
2. Konsist in `:architettura-test` (imports/packages/naming — `code-rules.md`).
3. detekt (style, error handling, `!!`) with `allWarningsAsErrors`.
4. ADR `enforced_by` rules (run by `mismagent-verifier`).

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
