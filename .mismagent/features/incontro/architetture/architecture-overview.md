# Architecture overview — incontro

> Feature-level view (architect, model movement, **feature dispatch**, 2026-10-01). The trunk style is unchanged:
> `.mismagent/architecture.md` (dated amendment 2026-10-01), `code-rules.md` (CR-10 note), `architetture/dev-architecture-app.md`
> (deletions list). Canonical names: `context-map.md`; invariants: `../tactical-model.md`; views: `../UI/ux-proposal.md`;
> why-ledger: `../decisions.md`. Single side `app`: every boundary is in-process.
> Shapes are pinned **once**, in the ADRs; this file points to them and never restates a type.

## Decision table
| # | Decision | Rationale | ADR |
|---|---|---|---|
| A-1 | `Incontro` root in `:progetto:dominio`; `OraDiInizio` VO; the order of the `Parte`s is ONE pure function in Progetto, delivered ordered and numbered to every reader | the map: order and numbers derived; one copy of [INV-I2] | [0033](../../../decisions/0033-incontro-progetto-chiavi-confini.md) §1 |
| A-2 | Kernel: `IncontroId`, `SegmentoRef`; `VoceRef = (incontroId, voceId)`; `EstrattoRef` unchanged | a `Voce` is the `Incontro`'s (D-0002); an extract is one `Parte` (D-0014) | [0033](../../../decisions/0033-incontro-progetto-chiavi-confini.md) §1 |
| A-3 | ONE `AggiungiRegistrazione` with destinations `NuovoIncontro \| IncontriSeparati \| Incontro(id)`; probe and copy outside, one transaction, all or nothing | D-0016 + the UX's all-or-nothing on every multi-file import | [0033](../../../decisions/0033-incontro-progetto-chiavi-confini.md) §2 |
| A-4 | Every boundary re-keyed by `incontroId` (Parlanti, Sbobinatura, Sintesi reads); a new Progetto → Sintesi `LettoreIncontro`; Sintesi's `statoParte` | consumer-owned ports, ISP | [0033](../../../decisions/0033-incontro-progetto-chiavi-confini.md) §4 |
| A-5 | The breaking re-key in two blocks in sequence *(amended 2026-10-01 [user])*: `persistenza-incontro` (wave 1) lands `7.sqm` with repositories joining `incontro_id` from `registrazione_id` — sound while every `Incontro` has one `Parte`; `incontro-chiavi` (wave 2) is the compile-checked sweep and removes the joins | no coexistence of two keys; behaviour-neutral on existing data | [0033](../../../decisions/0033-incontro-progetto-chiavi-confini.md) §6, [0034](../../../decisions/0034-persistenza-incontro-7sqm.md) |
| A-6 | `7.sqm`: `incontro`; `registrazione.incontro_id` (+ triggers) and `ora_di_inizio`; `voci_incontro` + `voce_incontro`; `voce` kept as per-`Parte` presence; Parlanti and Sintesi leaves rebuilt; `struttura` re-encoded | no rebuild of tables under immediate FKs; fail-closed FKs kept; nothing becomes `superato` | [0034](../../../decisions/0034-persistenza-incontro-7sqm.md) |
| A-7 | `VociDellIncontro` root (counter + one `Trascritto` per `Parte`); numbers and `segmentoId`s never reused; Revisione across `Parte`s; read-only widened to the `Incontro` | D-0007, D-0011, D-0012 | [0035](../../../decisions/0035-voci-dell-incontro.md) |
| A-8 | Parlanti per `Parte`: prints keyed by (`Parlante`, `VoceRef`, `Parte`); purge by `Parte` on `TrascrittoSostituito` / `TrascrittoEliminato`; `RiallineaImpronte` by `incontroId`, never INSERT; [INV-27] over the `Incontro` | exact purges; no resurrection | [0035](../../../decisions/0035-voci-dell-incontro.md) §6 |
| A-9 | Sbobinatura fan-out = every transcribed `Parte` of the `Incontro` on an `Incontro`-keyed event | stateless projection; idempotent output | [0035](../../../decisions/0035-voci-dell-incontro.md) §7 |
| A-10 | `PropostaTraParti` (option (a)): mutual, unique FORTE between unattributed `Voce`s sharing no `Parte`; existing `SoglieFascia`; one gesture, never automatic | spike closed by the user (D-0021, low confidence) | [0036](../../../decisions/0036-proposta-tra-parti.md) |
| A-11 | `Riassunto` per `Incontro`: one pass, labels `s1…sN` over the whole input, `Verifica` per `Parte`, `superato` derived from `StrutturaIncontro`; no automatic re-summary anywhere | D-0001, D-0004, D-0007, D-0010, D-0020; the measured input | [0037](../../../decisions/0037-riassunto-dell-incontro.md) |
| A-12 | Deleting a `Parte`: Parlanti purge behind a nested `TrascrittoEliminato`; `Riassunto` deleted only with the last `Parte`; `incontro` row removed by Progetto; [INV-28] restated; non-last dialog text | D-0003, [INV-I1], [INV-I6] | [0038](../../../decisions/0038-elimina-parte-dell-incontro.md) |
| A-13 | "Trascrivi" on an `Incontro`: one `Numero di persone`, one `Elaborazione` per untranscribed `Parte`, strictly increasing `creataAlle` in `Parte` order; prefill from the `Incontro` | D-0015; strict FIFO keeps the order | [0039](../../../decisions/0039-trascrivi-incontro-numero-persone.md) |

## Boundaries and their contract tests
Port shapes: [ADR 0033 §4](../../../decisions/0033-incontro-progetto-chiavi-confini.md). Each consumer port keeps its
`<Porta>Contratto` + `<Porta>Finta`; the real adapter runs the same contract, seeded through the supplier's commands
(dev-architecture `#porta-contratto`).

| Port (consumer) | Real adapter | What the contract asserts (consumer-driven) |
|---|---|---|
| `LettoreRegistrazione.parti` (Trascrizione, Parlanti) / `LettoreIncontro.parti` (Sintesi) | over `CatalogoRegistrazioni.incontro` | ordered by [INV-I2] (date, time empty last, import instant, id); numbers 1…N; an edit of date/time reorders; a deleted `Parte` disappears; `null` for an unknown or ceased `Incontro` |
| `LettoreVoci` (Parlanti) | over `VociDelTrascritto` | `VoceRef`s carry the `incontroId`; a `Voce` spanning two `Parte`s comes once, with intervals per `Parte`; after `unire` across `Parte`s the survivor holds both; `null` when no `Parte` is transcribed |
| `LettoreTrascritto` (Sbobinatura) | over `VociDelTrascritto` | `incontroId` on the `TrascrittoTesto`; segments carry `Incontro` numbers; `partiConTrascritto` lists only transcribed `Parte`s |
| `LettoreNomi` (Sbobinatura, Sintesi) | over `NomiDelleVoci` | attributed `Voce`s of the `Incontro` only; one name for a `Voce` in every `Parte`; `incontriCon(p)` |
| `LettoreTrascritto.statoParte` (Sintesi) | over `StatiElaborazione` | the four values, incl. a re-run open on a transcribed `Parte` = `IN_TRASCRIZIONE`, a failed first run = `NON_RIUSCITA` |
| `VociDellIncontroRepository` | SQL | round-trip of a multi-`Parte` root; counters survive the removal of every `Trascritto`; `prossimo_segmento` never decreases on replacement; `trascritto(r)` reads one `Parte` |
| `IncontroRepository` | SQL | round-trip; `partiDi`; `rimuovi` refused while a `Parte` exists (immediate FK) |

**Events (Published Language):** Progetto ADR 0033 §3; Trascrizione ADR 0035 §5; Sintesi ADR 0037 §1.
**Authorship:** reads consumer-driven, writes producer-driven. **Counter-proposals to the UX views** (recorded):
`IncontriDelProgetto` split per owner (ADR 0033 §5); `EstrattoRef` unchanged, the "parte n" label from `TrascrittoView.parti`;
the `Fonte` chip of a vanished `Segmento` shows no minute (ADR 0037 §6).

## Invariant → where it is enforced
| Invariant | Owner | Store / mechanical backstop |
|---|---|---|
| [INV-I1] one `Incontro` per `Registrazione`, immutable; never empty | `Registrazione` + `AggiungiRegistrazione` / `EliminaRegistrazione` | FK + two triggers on `registrazione.incontro_id`; immediate FKs to `incontro` |
| [INV-I2] total order | `OrdineDelleParti` (table test) | — |
| [INV-I3] 1-part = today | every view test + `7.sqm` migration test | render-check vs today's PNGs |
| [INV-I4] / [INV-I16] never reused | `VociDellIncontro` (invariant tests) | `voci_incontro.prossima_voce`, monotonic `trascritto.prossimo_segmento` |
| [INV-I5] / [INV-I6] / [INV-I7] | `VociDellIncontro` | deferred FKs `segmento → voce`, Parlanti → `voce_incontro` / `voce` |
| [INV-I8] / [INV-I8b] | `Parlante` + purge policy | `impronta_vocale` UNIQUE + two deferred FKs |
| [INV-I9] / [INV-I10] / [INV-I11] / [INV-I19] | `Riassumibilita`, `Riassunto`, `StrutturaIncontro`, `IngressoRiassunto` | `riassunto_*_unico` on `incontro_id` |
| [INV-I12] races | esegui-riassunto (CAS) | — |
| [INV-I12b] / [INV-28] deletion | the three policies | immediate FK `riassunto → incontro`; ADR 0037 check (no `TrascrittoSostituito` in Sintesi) |
| [INV-I17] / [INV-I18] | `EstrattoAudio`, `PropostaTraParti` | — |

## Aggregate confinement (rule 9) *(added 2026-10-01, build-manifest checkpoint [user])*
| Aggregate block | Writes only in its adapter | Mutated only through the root | Invariant fields read only inside |
|---|---|---|---|
| `incontro` | `adr-0034-tabelle-incontro-confinate.sh` (from `persistenza-incontro`) | `adr-0033-incontro-mutato-dalla-radice.sh` (from `incontro`) | `adr-0033-ordine-solo-nel-dominio.sh` (from `incontro`) |
| `voci-dell-incontro` | same ADR 0034 check | `adr-0035-voci-mutate-dalla-radice.sh` (from `porte-trascrizione-incontro`) | `adr-0035-contatori-solo-nella-radice.sh` (from `voci-dell-incontro`) |
| `parlante-impronte-per-parte` | same ADR 0034 check | `adr-0035-impronte-mutate-dalla-radice.sh` (from `parlante-impronte-per-parte`) | `adr-0035-impronte-lette-dalla-radice.sh` (from `parlante-impronte-per-parte`) |
| `riassunto-incontro` | same ADR 0034 check | `adr-0037-riassunto-mutato-dalla-radice.sh` (from `riassunto-incontro`) | `adr-0037-struttura-letta-dalla-radice.sh` (from `riassunto-incontro`) |

## Proposed block ids (the `from` of the new checks; build-manifest pins them)
- `incontro-chiavi` — the kernel sweep (ADR 0033 §6), with `persistenza-incontro`;
- `persistenza-incontro` — `7.sqm`, migration test, re-keyed repositories, checks `adr-0034-schema-incontro.sh` and
  `adr-0034-tabelle-incontro-confinate.sh`;
- `riassunto-incontro-politiche` — removal of the sostituzione policy, check `adr-0037-nessun-riassunto-automatico.sh`
  (the `RegistrazioneEliminata` re-scope is block `eliminazione-parte-sintesi`, wave 5);
- the aggregate blocks `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro` (wave 3) and
  `porte-trascrizione-incontro` (wave 4) — the rule-9 checks above.

**Gate note (for the conductor / worker-composer):** `ControlliAdrTest.ogni check citato da un ADR esiste` asserts every
cited script exists, regardless of `from`. From this commit until every owning block lands, that assertion is red on the feature branch (the same situation ADR
0029/0030 had). With the rule-9 checks (2026-10-01) the last owner is `porte-trascrizione-incontro` (wave 4), so the
red window now runs through wave 4, not only wave 1. Either accept that, or relax the assertion to checks whose
`from` block is integrated (a `gate_files` change, the user's call) — recommended now that the window spans four waves.

## NFRs, pinned as verifiable constraints
- **Time of a 3 h `Incontro` Riassunto** ≤ 10 min per hour of audio (ADR 0026), opt-in `@Tag("modelli")` /
  `benchmarkRiassunto` on a real 2–3 `Parte` `Incontro`, plus the user's judgement that no `Decisione` spanning two `Parte`s
  appears twice (D-0010; ADR 0037 Consequences). Not in the gate.
- **Transcription** stays ADR 0011 per `Parte` (≤ 10 min per hour); N `Parte`s run serially through the shared queue.
- **Privacy:** prints copied by the migration are zeroed and checkpointed (ADR 0034 §3); a non-last `Parte`'s deletion keeps
  derived text in a `superato` `Riassunto` — accepted by the user (D-0003), stated in the dialog.
- **Not yet verifiable:** the `Revisione` latency on a 3 h `Incontro` (D-0011 revisit) has no pinned bound; flagged.

## Open items
- Spike **`ora-di-inizio`**: closed 2026-10-01 by [ADR 0040](../../../decisions/0040-data-e-ora-da-udta-date.md) (D-0026):
  `DataRegistrazione` and `OraDiInizio` both come from `moov/udta/date`, read by a box reader in `:audio`; block
  `sonda-ora-di-inizio` also carries the date correction (AC-364). Only Voice Memos via AirDrop was measured (accepted gap).
- `tactical-model.md` header (lines 12–13) still calls `voci-tra-parti` OPEN at `tasks/app/backlog`: build-manifest should
  read the spike as closed (D-0021, ADR 0036).
