---
scope: global
status: accepted
supersedes: null
closes_spike: voci-tra-parti
decided: 2026-10-01 · user (closed spike voci-tra-parti with option (a) on one real two-part Incontro; the listening check and the second Incontro skipped) · architect (read-model placement, computation discipline, thresholds = the existing SoglieFascia)
from_note: incontro D-0021
enforced_by: []   # discursive + tests: the read-model writes nothing and keeps no embedding (ADR 0009, test on the fake extractor/repository); it never sends a command (it only exposes pairs; the S3 presenter sends UnisciVoci)
---
# 0036 — `PropostaTraParti`: a print-based proposal to join `Voce`s of different `Parte`s, mutual and unique FORTE only, never automatic

## Context
Spike `voci-tra-parti` (context-map, closed 2026-10-01 by the user, incontro D-0021) asked how the `Voce`s that each
`Parte`'s diarization produces become the `Voce`s of the `Incontro`. Evidence
(`features/incontro/spikes/voci-tra-parti.md`): one real `Incontro` in two contiguous files (18 + 75 min, 4 people).
With the automatic count the diarization was already wrong (2 and 5 `Voce`s). With `Numero di persone` = 4, the app's
existing `Proposta` gave **4/4 FORTE pairs, one-to-one**, consistent with the text. The pairs were not checked by ear
and only one `Incontro` was measured: **confidence low**. The tactical model fixed the eligibility as a working
hypothesis ([INV-I18], D-0017); the user fixed `Numero di persone` once per `Incontro` (D-0015, ADR 0039).

## Decision

### 1. The option and its rule
Option **(a)**: a print-based proposal confirmed by ONE user gesture, never automatic. Options (b) joint clustering
and (c) manual only are not adopted; the manual path (`unire` across `Parte`s, the name-driven `Proposta di unione`)
stays available and needs nothing from this ADR. **[INV-I18]** pairs `Voce` A with `Voce` B only if:
- both are `Voce`s of the same `Incontro`, both **unattributed**, and they share **no** `Parte`;
- B is A's ONLY FORTE among the eligible `Voce`s, and A is B's only FORTE (mutual, 1:1);
- neither is already in another proposed pair.

Anything else (two FORTE, a FORTE already taken, only DEBOLE) proposes nothing. Confirming sends ONE `UnisciVoci` with
the `Voce` whose first `Parte` is earlier ([INV-I2]) surviving. There is no "No": a pair disappears when the condition
stops holding.

### 2. Thresholds: the existing `SoglieFascia`, no cross-file pair of its own
The similarity of A and B is the **best cosine over their (`Voce`, `Parte`) slice pairs**, each slice's transient print
taken with `SorgenteImpronta.di` (ADR 0012 (b)) and compared by `ConfrontoImpronte` with the **same** `SoglieFascia`
the `Proposta` uses — today the provisional `forte` 0.7 / `debole` 0.5 (`ConfrontoImpronteCoseno`, ADR 0019 Amendment
(b).4). The spike measured exactly these values across two files; no separate cross-file thresholds are introduced.
When spike `impronta-vocale-affidabilita` calibrates `SoglieFascia`, the new values apply here too, and this ADR is
re-read against them.

### 3. Placement and computation
- Read-model **`PropostaTraParti`** in `:parlanti:applicazione ..letture`, per `incontroId`, view
  `[{ voceA, parteA, estrattoA: EstrattoRef, voceB, parteB, estrattoB: EstrattoRef }]` (UX § Data views); each
  `EstrattoRef` from [INV-I17].
- Ports it uses: `LettoreVoci.voci(incontroId)` (ADR 0033 §4), `DecodificatoreAudio`, `EstrattoreImpronta`,
  `ConfrontoImpronte`, `AttribuzioneRepository` (who is attributed). No new port.
- **Discipline (the `Proposta`'s, ADR 0009 / ADR 0012 (b) / ADR 0017):** extraction outside any transaction, under the
  shared sherpa Mutex, in the per-project scope; prints kept **in memory only**, never written or cached to disk; ONE
  extraction per eligible slice per computation; the result cached per `incontroId` and invalidated on `VociUnite`,
  `VoceDivisa`, `SegmentoRiassegnato`, `ElaborazioneCompletata`, `TrascrittoSostituito`, `TrascrittoEliminato`,
  `AttribuzioneConfermata`, `ImpronteRiallineate`; not computed while S3 is read-only (ADR 0035 §4); a cancelled
  computation leaves no cache entry.
- Cost: one extraction per unattributed slice (≤ 30 s of audio each, `BUDGET_IMPRONTA_MS`), on demand when S3 shows the
  `Incontro`; it is not in the ADR 0011 pipeline budget, as the `Proposta` is not.
- UI: the second banner kind of the Voci panel (UX), after the `Proposta di unione` banner (at most one banner per
  screen).

## Rejected options
- **(b) joint clustering**: automatic joins, and a wrong join costs a `dividere` across `Parte`s plus names on the wrong
  person; it would also make the pipeline `Incontro`-aware (other `Parte`s' prints at diarization time).
- **Proposing every FORTE pair with conflicts shown** (D-0017 option A): the evidence has no conflict case; showing
  ambiguous pairs invites wrong joins.
- **Separate cross-file thresholds**: nothing measured would set them differently.

## Consequences
- The read-model block and its banner are **no longer blocked** (the spike is closed); only the `ora-di-inizio` adapter
  stays blocked by a spike.
- **Revisit trigger:** a proposed pair turns out to be two different people. Then: disable the banner (manual path only)
  or give cross-file comparison its own thresholds, by amendment of this ADR.
- Context-map: the spike entry is `[x]` (closed by the user, D-0021); this ADR is its recording ADR. The ubiquitous
  language may name the proposal (`PropostaTraParti`, UX name) — owed to the analyst.
- Tests: table tests of [INV-I18] on fake prints (mutual, 1:1, shared `Parte`, attributed, two FORTE, FORTE taken, only
  DEBOLE); the "one extraction per slice" count; nothing written (fake repositories untouched). The real-adapter check
  stays the opt-in `SpikeIncontroTest` (`@Tag("modelli")`).
