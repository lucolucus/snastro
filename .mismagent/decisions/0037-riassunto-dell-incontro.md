---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0021 §3 (ports/events/commands keyed by incontroId), §4 (input labels), §6 (the sostituzione-trascritto policy and its subscriber REMOVED), §9 (deletions list); ADR 0022 §2 (struttura encoding); ADR 0023 §1/§4/§5 (queue items, PosizioniNellaCoda.riassunti and the best-effort cancel keyed by incontroId); ADR 0024 §1/§2 (delete only when the Incontro ceases); ADR 0018 §5 (Sintesi no longer consumes TrascrittoSostituito)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the user's D-0001, D-0003, D-0004, D-0007, D-0010, D-0020 and the tactical model's § Sintesi
from_note: incontro D-0010
enforced_by:
  - check: architettura-test/controlli-adr/adr-0037-nessun-riassunto-automatico.sh
    from: riassunto-incontro-politiche
    # PROHIBITION (scope as amended 2026-10-03): no `TrascrittoSostituito` (simple or fully-qualified name) in any *.kt under
    # sintesi/*/src/main AND under Sintesi's composition avvio/src/main/kotlin/snastro/avvio/sintesi/ (comments removed by
    # lib/senza-commenti.awk: nested and multi-line /* */, trailing //; a `//` inside a string is code; build dirs excluded) — Sintesi
    # never reacts to a re-transcription (D-0004, D-0007: no automatic Riassumi, superato is derived). FAIL when sintesi/ is missing,
    # when sintesi/*/src/main matches no directory, or when it holds no *.kt (an empty glob is not a PASS). Violating fixtures (also a
    # subscription wired in avvio/…/avvio/sintesi): an import of snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
    # in sintesi/adattatori, a `is TrascrittoSostituito ->` branch in sintesi/applicazione, a fully-qualified use; conforming:
    # the name only in a KDoc line and in a // comment, `TrascrittoEliminato` used.
  - check: architettura-test/controlli-adr/adr-0037-riassunto-mutato-dalla-radice.sh
    from: riassunto-incontro
    # PROHIBITION (§9, riassunto-incontro: state mutated only through the root; clause 1 an ALLOW-LIST, amended 2026-10-03): (1) the file
    # of sintesi/applicazione/src/main declaring `interface RiassuntoRepository` (found by content) declares ONLY funs named salva
    # (salva(Riassunto)), concludi (the compare-and-set concludi(Riassunto)), rimuovi*, trova*, inAttesa, inCorso — any other name fails,
    # mutators included; a new port method is an amendment of this list; (2) in sintesi/dominio/src/main the element and Fonte types
    # (`Decisione`, `QuestioneAperta`, `Azione`, `PuntoChiave`, `Fonte`, `StrutturaIncontro`, `StrutturaTrascritto`, found by their `class`
    # declarations) declare no `var` and no public `fun` returning `Esito<` (on the `fun` line or on a `): Esito<` continuation line) — only
    # the root `Riassunto` changes state. Comments removed by lib/senza-commenti.awk; build dirs excluded. FAIL when no file declares
    # `class Riassunto` or `interface RiassuntoRepository`. Violating fixtures: `fun aggiornaStruttura(` and `fun contaPronti(` in the port,
    # `var testo` in Decisione, `public fun verifica(…): Esito<…>` in StrutturaIncontro; conforming: `concludi(r: Riassunto)`,
    # `trovaPronto(…)`, `private var _stato` in Riassunto.
  - check: architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh
    from: riassunto-incontro
    # PROHIBITION (§9 as amended 2026-10-03, riassunto-incontro: invariant fields read only inside the aggregate — [INV-I11]: `superato`
    # is decided only by `Riassunto.superato(corrente)`). The recorded structure of the root is the property `strutturaRegistrata`
    # (renamed from `struttura`, so its name is unique in the codebase). In every */src/main *.kt OUTSIDE sintesi/dominio/src/main and
    # sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/ (the only legitimate reader, the mapping): (1) no
    # occurrence of the identifier `strutturaRegistrata` in any form (`.x`, `?.x`, `::x`, bare in a scope function, inside `when (…)` or
    # `.equals(…)`); (2) in sintesi/applicazione/src/main, sintesi/adattatori/src/main (persistenza/ exempt), ui/src/main and
    # avvio/src/main only (other contexts own unrelated `chiave`s, e.g. SorgenteImpronta.chiave), no `chiave` member access (`.chiave`,
    # `?.chiave`, `::chiave`) — a structure's key is compared only by the root.
    # Comments removed by lib/senza-commenti.awk; build dirs excluded. FAIL when no file of sintesi/dominio/src/main declares
    # `strutturaRegistrata` (target missing). Violating fixtures: `r.strutturaRegistrata != corrente.chiave` in letture,
    # `with(r) { strutturaRegistrata }` in ui, `Riassunto::strutturaRegistrata` in avvio, `when (r.strutturaRegistrata)` and
    # `corrente.chiave.equals(k)` in sintesi/applicazione; conforming: `riassunto.superato(corrente)`, `completa(esecuzione.bozza,
    # esecuzione.struttura, …)`, `strutturaRegistrata = riga.struttura` in the persistence mapping, the name in a KDoc.
---
# 0037 — The `Riassunto` of an `Incontro`: one pass over the `Parte`s, input labels, `Verifica` per `Parte`, `superato` derived from a per-`Parte` structure, no automatic re-summary

## Context
The user moved the `Riassunto` from the `Registrazione` to the `Incontro` (D-0001), decided it is made in ONE pass over
the concatenated `Parte`s (D-0010, spike `contesto-lungo` closed), that it becomes `superato` and is never redone by
itself after any change of a `Parte` (D-0003, D-0004, D-0007), and that "Riassumi" waits for every `Parte` (D-0020).
The tactical model (§ Sintesi, conflicts 3–6) leaves the shapes to the architect. The spike's benchmark
(`BenchmarkRiassuntoTest`) labelled the concatenated input with **sequential** numbers `s1…sN` and no `Parte` separator:
that is the measured input.

## Decision

### 1. Re-keying (amends ADR 0021 §3, ADR 0023)
- `Riassunto.incontroId` (ADR 0034 §1); commands `Riassumi(incontroId, argomento?)`, `EseguiProssimoRiassunto`,
  `RecuperaRiassuntiInterrotti` unchanged otherwise; events `RiassuntoRichiesto|Avviato|Pronto|Fallito|Eliminato(incontroId…)`.
- Queue: `RiassuntiInAttesa` items `(riassuntoId, incontroId, richiestoAlle)`; `:ui` port
  `PosizioniNellaCoda.istantanea().riassunti: Map<IncontroId, Int>` (exact: at most one open `Riassunto` per `Incontro`,
  [INV-S2]); best-effort cancel `EsecuzioniRiassunto.annulla(incontroId)` on `RiassuntoEliminato`. The shared FIFO and its
  order key are unchanged (ADR 0023 §2).
- Ports: `LettoreTrascritto` (per `Parte`, `statoParte`), `LettoreNomi.nomi(incontroId)`, new `LettoreIncontro.parti(incontroId)`
  — shapes in ADR 0033 §4.

### 2. "Riassumi" guard [INV-I9] (amends [INV-S6]; one pure `Riassumibilita`, shared by command and view)
Accepted only if, read in its transaction: the LLM model is `Installato`; every `Parte` is `TRASCRITTA`; [INV-S2] holds;
the whole input fits `LimiteIngresso` (ADR 0026, constants unchanged); the `Argomento` is within its bound. The view's
`NonDisponibile` reasons name the **first** blocking `Parte` in [INV-I2] order: `DA_TRASCRIVERE` → "Manca la
trascrizione della parte n", `IN_TRASCRIZIONE` → "Parte n in trascrizione", `NON_RIUSCITA` → "Parte n non riuscita:
riprova o eliminala" (D-0020). The too-long refusal keeps its names (`troppo_lunga`, `IngressoTroppoLungo`): only its
scope is now the `Incontro`.

### 3. The input: one pass, sequential labels [INV-I19] (amends ADR 0021 §4)
- Order: `Parte` after `Parte` in [INV-I2] order; within a `Parte`, `Segmento`s in [INV-7] order. No `Parte` separator
  line (the measured input had none).
- Line: `[s<k> V<n>] <testo>`, where **k = the 1-based position of the `Segmento` in this input** (1…N over the whole
  `Incontro`) and n its `Incontro` `Voce` number; legend `V<n> = Voce n` (ADR 0032, unchanged).
- `IngressoRiassunto.costruisci(parti)` returns the text **and** the label table `etichette: List<SegmentoRef>` (label k ↔
  `etichette[k-1]`). The table lives in memory for the run only.
- **`ModelloLinguistico` is unchanged in shape**: `fonti: List<Int>` are now input labels, not `segmentoId`s. Why labels
  rather than `(parte, segmentoId)` pairs: a pair needs a new answer grammar and more tokens (Qwen spends one token per
  digit, ADR 0021 Amendment 2026-09-26), and `segmentoId`s grow across re-transcriptions ([INV-I16]); sequential labels
  are the shortest unique key and are what the spike measured.
- A 1-part `Incontro` uses the same rule (labels 1…N); its new `Riassunto`s may differ from today's input only by the
  label numbers. [INV-I3] concerns screens and stored data, not the LLM input.

### 4. `Verifica delle fonti` per `Parte` [INV-I10] (amends [INV-S4])
Each answer label is mapped through the run's table; a label outside 1…N is a dropped `Fonte` (existing drop and count
rules). A kept `Fonte` is stored as `(registrazioneId, segmentoId)` (`riassunto_fonte`, ADR 0034). A bound speaker must be
a `Voce` of the `Incontro` as read for the run; a `PuntoChiave`'s speaker must be among the `Voce`s of its valid
`Fonte`s, which may lie in different `Parte`s.

### 5. `superato` derived, never stored [INV-I11] (amends ADR 0022 §2's encoding)
- **`StrutturaIncontro`** (`:sintesi:dominio`): the ordered `Parte`s, each with its `StrutturaTrascritto`.
- **`chiave`** = for each `Parte` in [INV-I2] order, `<registrazioneId>=<StrutturaTrascritto.chiave>`, joined by `;`
  (e.g. `r1=1:1,2:2;r2=1:3,2:1`). `registrazioneId` is the `RegistrazioneId.valore` text (UUID: never contains `=` or
  `;`), so the encoding is exact and collision-free.
- **Recorded** (on `pronto`): the `Parte`s as read for the run that had a `Trascritto`. **Current** (on display):
  every `Parte` of the `Incontro` now, a `Parte` with no `Trascritto` written `<registrazioneId>=` (empty).
- `superato` ⇔ `pronto` and current `chiave` ≠ recorded. This catches a `Revisione` across `Parte`s, every completed
  (re)transcription ([INV-I16] gives new ids, [INV-I4] new numbers — no generation number needed), a reorder, an
  eliminated or an imported `Parte`. Names, `Attribuzione`s, `ConfermaSegmento` do not change it. A change that restores
  the exact structure clears it.
- **Migration**: a 1-part `chiave` is `<id>=` + the old encoding, which is exactly what `7.sqm` writes
  (`registrazione_id || '=' || struttura`); an unchanged `Trascritto` compares equal ([INV-I3], ADR 0034 §4).

### 6. `riassunto-vista` (per `incontroId`; consumer: the Riassunto tab of every `Parte` page)
- `numParti`; `disponibilita` per §2; `superato` per §5; queue position joined by the presenter (ADR 0023 §4).
- **`VoceVista.presente`** = the `Voce` is in the current structure (read through `LettoreTrascritto`); `false` renders
  "Voce n · non più presente" — never a `Nome`, never a live "Voce n" [INV-I13]. Names come from
  `LettoreNomi.nomi(incontroId)` for present `Voce`s only.
- **`FonteVista(registrazioneId, numeroParte: Int?, segmentoId, voce: VoceVista, inizioMs: Long?, segmentoPresente)`**:
  `numeroParte` null when the `Parte` is no longer in the `Incontro`; `inizioMs` null when the `Segmento` no longer
  exists (Sintesi stores no interval). *Counter-proposal to the UX chip "parte 2 · 12:30 · non più presente"*: for a
  vanished `Segmento` the minute is unknown, and for an eliminated `Parte` so is its number; the chip then shows
  "parte n · non più presente" or, with no `Parte`, "non più presente" (not clickable).

### 7. Policies (amends ADR 0021 §6, ADR 0024)
- **The sostituzione-trascritto Sintesi policy and `AbbonatoTrascrizioneSintesi` are REMOVED.** No automatic
  "Riassumi" exists any more, 1-part included [user D-0004, D-0007]. Sintesi never references `TrascrittoSostituito`
  (`enforced_by` above).
- **`RegistrazioneEliminata` policy** (synchronous, never vetoes): if `incontroCessato`, remove every `Riassunto` of the
  `Incontro` in any state and publish `RiassuntoEliminato`; otherwise do nothing — the `Riassunto` turns `superato` by
  derivation and its `Fonte`s naming the eliminated `Parte` survive [user D-0003, privacy cost accepted]. ADR 0038.

### 8. Races [INV-I12]
- A queued `Riassunto` reads the `Incontro` when it is claimed: a `Parte` without a `Trascritto` at that moment is left
  out and the `Riassunto` is born `superato`; if no `Parte` has one, it ends `fallito` `nessun_contenuto_verificabile`.
- A running one completes born `superato` if the structure changed meanwhile.
- An `Incontro` that ceased during the run: its rows were deleted by §7, the compare-and-set finds none and writes
  nothing (ADR 0022 §4).

### 9. Aggregate confinement (rule 9) *(added 2026-10-01, build-manifest checkpoint [user])*
For `riassunto-incontro` (`invariant_fields`: `incontroId`, `struttura`, `elementi.fonti`; tables `riassunto`,
`riassunto_elemento`, `riassunto_fonte`): writes only in its adapter → ADR 0034 `adr-0034-tabelle-incontro-confinate.sh`;
mutated only through the root → `adr-0037-riassunto-mutato-dalla-radice.sh`; invariant fields read only inside →
`adr-0037-struttura-letta-dalla-radice.sh` (the `Fonte`s and `incontroId` are shown by the view as read copies; the
decision they carry, `superato`, is the root's).

## Rejected options
- **`(parte, segmentoId)` labels in the input** (`[s2.143 V5]`): new grammar, more tokens, unmeasured.
- **Split per `Parte` and recompose**: rejected by D-0010.
- **Storing a generation number to detect re-transcriptions**: [INV-I16] + [INV-I4] make the structure change by itself.
- **Keeping the automatic re-summary for 1-part `Incontro`s**: D-0007 wants one rule.
- **Asking Parlanti whether a `Voce` still exists**: Trascrizione owns that fact, and Sintesi already reads it (§6).

## Consequences
- `IngressoRiassunto`, `Riassumibilita`, `StrutturaIncontro`, the `Riassunto` root, `riassunto-vista` and the queue source
  change. The sostituzione policy, its subscriber and its tests are deleted by block `riassunto-incontro-politiche`
  (wave 1, owner of this ADR's first check); the `RegistrazioneEliminata` policy's re-scope (§7, delete only when
  `incontroCessato`) is the separate block `eliminazione-parte-sintesi`, because it needs the amended event.
- `features/sintesi`'s manifest is not edited; this feature's manifest carries the new ACs.
- **NFR (D-0010):** a real 2–3 `Parte` `Incontro` of ≈ 3 h summarized in ≤ 10 min per hour of audio (ADR 0026), measured
  opt-in (`@Tag("modelli")` / `benchmarkRiassunto`), with the user judging that no `Decisione` spanning two `Parte`s
  appears twice — a `tests_nl` of the esegui-riassunto block (build-manifest). Not verifiable in the gate.
- Enforcement: the prohibition above; the label round-trip, [INV-I10] and [INV-I11] table tests; the migration test of
  ADR 0034. Discursive (code review): the label table never leaves the run; the policy deletes only on `incontroCessato`.
