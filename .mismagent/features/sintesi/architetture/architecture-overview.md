# Architecture overview — sintesi

> Feature-level view (architect, model movement, **feature dispatch**, 2026-09-25). The trunk is
> unchanged in style: `.mismagent/architecture.md` (amended with pointers to ADR 0021–0025),
> `.mismagent/code-rules.md`, `.mismagent/architetture/dev-architecture-app.md`.
> Canonical names come from `.mismagent/context-map.md` (§ Sintesi), the invariants from
> `../tactical-model.md`, and the views from `../UI/ux-proposal.md`. Single side `app`: every
> boundary is **in-process**.
> Shapes are pinned **once**, in the ADRs. This file points to them and never restates a type.

## Decision table
| # | Decision | Rationale | ADR |
|---|---|---|---|
| D-1 | Modules `:sintesi:dominio\|applicazione\|adattatori` + technical `:llm`; the edges added; no context depends on Sintesi; `:sintesi:*` never reaches `:modelli` | the trunk pattern (ADR 0002). The runtime is confined like sherpa. The download progress lives in `:avvio` | [0021](../../../decisions/0021-sintesi-moduli-confini-porte.md) §1–2 *(2026-09-26: `:llm` → the separate library `:llama-jni`, no edges — [ADR 0027](../../../decisions/0027-libreria-llama-jni-separata.md))* |
| D-2 | Every boundary is a Sintesi-owned port + contract test: `LettoreTrascritto`, `LettoreNomi`, `ModelloLinguistico`, `DisponibilitaModelloLinguistico`, and synchronous subscribers to `TrascrittoSostituito` / `RegistrazioneEliminata` | consumer-driven reads, ISP, D1/D2 | [0021](../../../decisions/0021-sintesi-moduli-confini-porte.md) §3, §6 |
| D-3 | `ModelloLinguistico` is runtime-neutral: structured answer, `{V<n>}` speaker syntax, cancellable, never called inside a transaction; the prompt/schema live in the adapter | either JNI or a sidecar fits; the prompt never reaches the domain or storage | [0021](../../../decisions/0021-sintesi-moduli-confini-porte.md) §4–5 |
| D-4 | [INV-S5] mechanical: no `ParlanteId` / `snastro.parlanti.` in Sintesi's dominio/applicazione; no `nome`/`parlante_id` column; table ownership both ways | a rename never invalidates a `Riassunto` | [0021](../../../decisions/0021-sintesi-moduli-confini-porte.md) §7–8 (`enforced_by`), [0022](../../../decisions/0022-persistenza-sintesi-6sqm.md) (`enforced_by`) |
| D-5 | `6.sqm`: `riassunto` (+ cap column, canonical `struttura` TEXT), `riassunto_elemento`, `riassunto_fonte`, `impostazioni_sintesi`; partial unique indexes `riassunto_non_pronto_unico` / `riassunto_pronto_unico`; IMMEDIATE FK to `registrazione` only | FTS-ready rows; `superato` = one equality; set rules on the store; fail-closed delete | [0022](../../../decisions/0022-persistenza-sintesi-6sqm.md) |
| D-6 | Completion = in-transaction compare-and-set (re-read; remove the previous `pronto`; conditional UPDATE) | [INV-S3] + [INV-S8]: no resurrection, the shown `Riassunto` kept on failure | [0022](../../../decisions/0022-persistenza-sintesi-6sqm.md) §4 |
| D-7 | ONE shared FIFO queue owned by `:avvio` (`CodaCondivisa`, multi-source). Each context claims its own head, bounded by the other source's head. Strict FIFO | user decision; neither context reads the other's rows | [0023](../../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md) §1–3 |
| D-8 | The queue position is computed by the owner (`:ui` port `PosizioniNellaCoda`) and joined in the presenters; `stati-elaborazione.posizioneInCoda` is removed | one source of truth across both kinds | [0023](../../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md) §4 (`enforced_by`) |
| D-9 | The LLM does not take the sherpa Mutex; the model is unloaded after each run (default) | no shared native state; a `Conferma` never waits minutes | [0023](../../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md) §5 |
| D-10 | "Elimina registrazione" also deletes every `Riassunto` (third synchronous subscriber, no veto); [INV-28] and the dialog text are amended | privacy, user decision; fail-closed FK | [0024](../../../decisions/0024-elimina-registrazione-riassunto.md) |
| D-11 | An optional catalogue entry (`obbligatoria=false`), downloaded on demand from the Riassunto tab; immutable ungated HTTPS host; FILE assets moved, not copied; free-space pre-check; RC-8 amended | Q-S1 "download only"; 6.6 GB asset | [0025](../../../decisions/0025-modello-facoltativo-su-richiesta.md) |
| D-12 | Composition R3 (`avvio-sintesi`) on top of R2 | `LettoreNomi` needs Parlanti; releases stay functional | [0021](../../../decisions/0021-sintesi-moduli-confini-porte.md) §10 |

## Boundaries and their contract tests
Port shapes: [ADR 0021 §3–4](../../../decisions/0021-sintesi-moduli-confini-porte.md). Each port has
an abstract `<Porta>Contratto` + `<Porta>Finta` in `:sintesi:applicazione` `testFixtures`. The fake
runs the contract in the gate (D1). The real adapter runs the same contract (D2): it seeds the
supplier **through the supplier's own commands** on `databaseInMemoria()` (dev-architecture
`#porta-contratto`).

| Port | Real adapter (module) | What the contract asserts (consumer-driven) |
|---|---|---|
| `LettoreTrascritto` | `LettoreTrascrittoDaTrascrizione` (`:sintesi:adattatori ..porte`) over `VociDelTrascritto.segmenti` + `StatiElaborazione` | `null` without a `Trascritto` (never completed, running, fallita) · after a `Revisione` each `Segmento` carries its **current** `voceId` with unchanged `segmentoId`/interval/text ([INV-8]) · order as the supplier's ([INV-7]) · `elaborazioneAperta` true for `in_attesa` and for `in_corso`, false after `completata`/`fallita`/`annullata` · during a queued re-run the old `Trascritto` is still returned · inside the re-run's completion transaction the **new** `Trascritto` is returned (the sostituzione policy reads it) |
| `LettoreNomi` | `LettoreNomiDaParlanti` over `NomiDelleVoci.nomi` | attributed `Voce`s only · the current `Nome` after `RinominaParlante` · an `eliminato` still resolves · every key belongs to the asked `Registrazione` · an empty map for an unknown one |
| `ModelloLinguistico` | `:sintesi:adattatori ..ml` over `:llm` (`@Tag("modelli")`, **blocked by spike `runtime-llm-in-app`**) | answer structurally complete · speakers only as `{V<n>}` · `annullato()` / interrupt → `Errore(Annullato)` within the bound the spike states · the fake throws if a transaction is open (ADR 0012 (b) pattern) · Fonte validity is **not** asserted (it is the root's job) |
| `DisponibilitaModelloLinguistico` | `:avvio`, over the `ServizioModelli` state holder + `ProvisioningModelli.installata(id)` | the four states and their transitions (`NonInstallato` → `InDownload` → `Installato` \| `DownloadFallito(motivo)`), `Installato` only when the marker matches, and after a restart a partial download reads `NonInstallato` |
| `RiassuntoRepository` / `LunghezzaMassimaRiassuntoRepository` | `…RepositorySql` (`:sintesi:adattatori ..persistenza`) | round-trip (elements, `Fonte` sets, tokens, `struttura`, cap) · both partial indexes → `RiassuntoGiaAperto` / backstop · the CAS race cases · FIFO `inAttesa` · no row ⇒ default 2000 |
| Subscribers | `AbbonatoTrascrizioneSintesi`, `AbbonatoProgettoSintesi` (synchronous) | registered before the first command (R3). The e2e on real SQLite proves the in-transaction purge and the fail-closed FK |

**Authorship:** the reads are consumer-driven (the shapes above come from the tactical model's
seam granularity and the UI views). The writes are producer-driven (Sintesi's commands).
Trascrizione and Parlanti publish events and never know Sintesi.

**Arbitrations recorded:**
- **Queue position.** The consumer-driven `RiassuntoVista.richiestaAperta.InAttesa{posizioneInCoda}`
  is **counter-proposed**: the view carries `richiestoAlle`, and the presenter joins the position
  from `PosizioniNellaCoda` (ADR 0023 §4). A Sintesi read-model computing it would have to read
  Trascrizione rows.
- **Model state.** `RiassuntoVista.modello` is read through `DisponibilitaModelloLinguistico`, while
  the download is triggered through `ServizioModelli`. Both are implemented in `:avvio` over one
  state holder (ADR 0025 §4).

## Invariant → where it is enforced
| Invariant | Owner | Store / mechanical backstop |
|---|---|---|
| [INV-S1] states, content iff `pronto` | `Riassunto` root | `CHECK`s on `riassunto` (ADR 0022) |
| [INV-S2] one open per `Registrazione` | `riassumi` service pre-check | `riassunto_non_pronto_unico` |
| [INV-S3] one `pronto` + one other | services (remove `fallito` / previous `pronto` in-transaction) | both partial indexes |
| [INV-S4] Verifica delle fonti | `Riassunto` root, table tests | — |
| [INV-S5] speakers only as `Voce` refs | root + `TestoConVoci` | ADR 0021 + ADR 0022 `enforced_by` |
| [INV-S6] Riassumi guards | `Riassumibilita` (pure, shared by service + view) | — |
| [INV-S7] `superato` derived | `StrutturaTrascritto.chiave` equality | nothing stored but `struttura` |
| [INV-S8] no survival / no resurrection | the policies + CAS completion | IMMEDIATE FK to `registrazione` |
| [INV-S9] cap within [300, 2500] | VO (provisional bounds) | SQL `> 0` only |
| [INV-S10] cap fixed at request, not verified | root + `RichiestaRiassunto` | `riassunto.lunghezza_massima_parole` |

## NFRs, pinned as verifiable constraints
- **Time:** ≤ 300 s for a 60-min `Registrazione` on the M3 Pro, measured on the real
  `ModelloLinguistico` adapter (`@Tag("modelli")`, opt-in, and `benchmarkRiassunto`) (ADR 0023 §6).
- **Privacy / offline:** no text leaves the machine. Network I/O stays confined to `:modelli` (ADR
  0008 `enforced_by`, unchanged). Any loopback exception for a sidecar is limited to `127.0.0.1` in
  `./llm/` and is fixed by the spike ADR (ADR 0021 §5, ADR 0025 §5).
- **Reliability:** a crash while `in_corso` gives `fallito` `interrotto` at the next start
  (`RecuperaRiassuntiInterrotti`). The shown `Riassunto` survives every failure ([INV-S3]).
- **Memory:** the model is unloaded after each run (default). Peak RSS is measured by the spike.
  **Not yet verifiable:** no RSS ceiling is pinned until the spike measures one (flagged below).

## Deferred to spikes (no first-release block waits, except the ones named)
| Spike | What it fixes | Blocks it gates |
|---|---|---|
| `runtime-llm-in-app` | JNI vs sidecar; which trunk `enforced_by` (ADR 0004 or 0008) admits `./llm/`; natives per ADR 0016; Metal; keep-warm vs unload; cancellation bound; `LimiteIngresso` calibration; the [INV-S9] upper bound; catalogue entry values | `:llm` + the real `ModelloLinguistico` adapter; the `.dmg` packaging; the e2e with the real runtime |
| `qualita-riassunto` | answer schema v1 + prompt; adherence to the cap; confirmation of the [300, 2500] bounds | the adapter's prompt/schema (the fake uses the provisional v1) |
| `filtro-fuori-tema` | the `Argomento` wording + bound (provisional 200) | adapter prompt; the `Argomento` VO bound |
| `contesto-lungo` | recordings > ≈ 1 h 15 | lifting the limit in `Riassumibilita` + a split step in `esegui-riassunto` |

No ADR of this dispatch satisfies a spike's closure criterion, so no spike is closed.

## Proposed block ids (build-manifest pins them)
- `scaffold-sintesi`: modules, edges, Konsist additions.
- `persistenza-sintesi`: `6.sqm` and the `.sq` query files.
- The `riassunto` and `lunghezza-massima-riassunto` aggregates.
- The ports: `lettore-trascritto-sintesi`, `lettore-nomi-sintesi`, `modello-linguistico`,
  `disponibilita-modello-linguistico`.
- Services and policies: `riassumi`, `esegui-riassunto`, `modifica-lunghezza-massima-riassunto`,
  `sostituzione-trascritto-sintesi-policy`, `eliminazione-registrazione-sintesi-policy`.
- Read-models: `riassunto-vista`, `impostazioni-sintesi`.
- The subscribers `abbonato-trascrizione-sintesi` and `abbonato-progetto-sintesi`.
- The rework blocks: `avvio-coda-condivisa` (with the Trascrizione deltas of ADR 0023) and the
  `modelli-provisioning` rework (ADR 0025).
- The UI blocks: `scheda-riassunto`, `schermata-registrazione` (tabs), and `avvio-sintesi` (R3).
- `modello-linguistico-llama`, blocked by the spike.

## Points flagged (ambiguity / not yet verifiable)
1. **Build dependency on the sibling branch:** `RegistrazioneEliminata` and `5.sqm` (ADR 0020) are
   not on this branch yet. `persistenza-sintesi` and `abbonato-progetto-sintesi` wait for that merge,
   or a rebase onto it.
2. **Memory NFR:** there is no ceiling until `runtime-llm-in-app` measures peak RSS. It is recorded
   as a measurement, not a constraint.
3. **A literal name written by the model into free text** cannot be detected mechanically (ADR 0021
   §7 blind spot). It is measured by `qualita-riassunto` and forbidden by the prompt.
4. **`LimiteIngresso` is provisional.** Its constants live in [ADR 0021 §5](../../../decisions/0021-sintesi-moduli-confini-porte.md)
   and in `LimiteIngresso` only. The runtime spike recalibrates them.
5. **Context-map notes owed by the analyst:** the context-map is read-only for this dispatch. The
   following notes now have a deciding ADR and should cite it:
   - Sintesi UL "provisioned through `:modelli` like every other model" → optional, on demand, ADR 0025;
   - "ADR 0020 … pending on the sibling branch" → Sintesi's part is ADR 0024;
   - Relationships "Execution (technical)" → ADR 0023;
   - the term "lunghezza massima del Riassunto" is still missing from the UL (the tactical model's
     language gap).
