---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0018 §1 ([INV-12] per generation → [INV-I4]; counters never restart; the rejected "monotonic numbering" becomes the rule), §3 (purge of the whole Registrazione → [INV-I8b]), §4 (Ritrascrivi dialog, multi-part variant), §5 (payload; Sintesi no longer a consumer), Amendment (b) §2 (read-only widened to the Incontro); ADR 0009 (prints per Parte, purge by Parte); ADR 0012 (b) (RiallineaImpronte by incontroId, still never INSERTs); ADR 0019 ([INV-27] over the Incontro); architecture.md (Trascrizione / Parlanti / Sbobinatura rows)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the user's D-0002, D-0007 and the tactical modeler's D-0011, D-0012, D-0014
from_note: incontro D-0011
amended: 2026-10-01   # build-manifest checkpoint [user]: rule-9 confinement checks (§9)
enforced_by:   # §9 (rule 9). Writes-only-in-the-adapter is ADR 0034's adr-0034-tabelle-incontro-confinate.sh; ADR 0018's and ADR 0012 (b)'s prohibitions stay in force unchanged
  - check: architettura-test/controlli-adr/adr-0035-voci-mutate-dalla-radice.sh
    from: porte-trascrizione-incontro
    # PROHIBITION (voci-dell-incontro: state mutated only through the root): (1) the class `Trascritto` (the file of trascrizione/dominio
    # declaring `class Trascritto`, found by content) declares no `public fun` whose return type starts with `Esito<` — its commands are
    # `internal`, called only by the root `VociDellIncontro`; (2) in trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/porte/
    # no `fun salva(` takes a parameter of type `Trascritto` (the only persistent entry is `VociDellIncontroRepository.salva(VociDellIncontro)`),
    # and the interface `VociDellIncontroRepository` declares no fun named aggiorna*/modifica*/imposta*/sposta*/cambia*/scrivi*. Comment lines
    # stripped. FAIL when no file declares `class VociDellIncontro` or `interface VociDellIncontroRepository` (target missing). `from` is the
    # port block because clause (2) is red until it retires TrascrittoRepository. Violating fixtures: `public fun unisci(…): Esito<VociUnite>`
    # in Trascritto, `fun salva(t: Trascritto)` in porte/, `fun aggiornaContatore(` in VociDellIncontroRepository; conforming: `internal fun
    # unisci(…): Esito<…>` in Trascritto, `public fun unisci` in VociDellIncontro, `public val segmenti` in Trascritto.
  - check: architettura-test/controlli-adr/adr-0035-contatori-solo-nella-radice.sh
    from: voci-dell-incontro
    # PROHIBITION (voci-dell-incontro: invariant fields read only inside the aggregate — [INV-I4], [INV-I16]): the identifiers `prossimaVoce`,
    # `prossimoSegmento` (and `_`-prefixed) appear in */src/main only under trascrizione/dominio/src/main and
    # trascrizione/adattatori/src/main/kotlin/snastro/trascrizione/adattatori/persistenza/ (the mapping). Comment lines stripped. FAIL when
    # trascrizione/dominio is missing. Violating fixtures: `voci.prossimaVoce` in trascrizione/applicazione/letture, `prossimoSegmento` in
    # avvio/; conforming: both in dominio and in the persistence mapping, the names in a KDoc.
  - check: architettura-test/controlli-adr/adr-0035-impronte-mutate-dalla-radice.sh
    from: parlante-impronte-per-parte
    # PROHIBITION (parlante-impronte-per-parte: state mutated only through the root; clause 2 an ALLOW-LIST, amended 2026-10-03):
    # (1) no construction of `ImprontaVocale` (`ImprontaVocale(`, simple or qualified, or `::ImprontaVocale`) in */src/main outside
    # parlanti/dominio/src/main and parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/ (a print is created only by
    # Parlante's methods, rebuilt only by its repository); (2) the file of parlanti/applicazione/src/main declaring `interface
    # ParlanteRepository` (found by content) declares ONLY funs named trova, delProgetto, nomeAttivoInUso, salva, rimuovi,
    # impronteDiRegistrazione, impronteDelProgetto and `aggiornaImpronta` (the compare-and-set of `RiallineaImpronte`, ADR 0012 (b)) — any
    # other name fails, mutators included; a new port method is an amendment of this list; (3) ImprontaVocale is a data class, so `.copy(`
    # is a construction too: in the files of clause 1, a file naming `ImprontaVocale` or reading `.impronte`/`::impronte` has no `.copy(`.
    # Comments removed by lib/senza-commenti.awk; build dirs excluded. FAIL when parlanti/dominio/src/main is missing, no file there declares
    # `class Parlante`, or no file declares `interface ParlanteRepository`. Violating fixtures: `ImprontaVocale(ref, …)` and
    # `::ImprontaVocale` in parlanti/applicazione/politiche, `fun rimuoviImpronteDellaParte(` in ParlanteRepository, `i.copy(voceRef = n)` on a
    # print in a comandi file; conforming: the constructor in dominio and in the persistence mapping, `aggiornaImpronta` in the port.
  - check: architettura-test/controlli-adr/adr-0035-impronte-lette-dalla-radice.sh
    from: parlante-impronte-per-parte
    # PROHIBITION (parlante-impronte-per-parte: invariant fields read only inside the aggregate — [INV-I8], [INV-I8b], [INV-21]; scope as
    # amended 2026-10-03): (1) no read of the Parlante property `impronte` (`.impronte`, `?.impronte`, `::impronte`; `.impronteDiRegistrazione`
    # & co. are other names) in any *.kt under parlanti/applicazione/src/main EXCEPT …/applicazione/letture/ (the read-models may read prints
    # to compare, Galleria) and under parlanti/adattatori/src/main EXCEPT …/adattatori/persistenza/ (the mapping) — so comandi/, politiche/,
    # any other applicazione package and the adattatori subscribers (eventi/, porte/) alike; (2) implicit receiver: in comandi/, politiche/,
    # adattatori/eventi/ and adattatori/porte/ the bare identifier `impronte` (scope functions `with`/`run`/`apply`) fails too, except as a
    # declaration (`val impronte`, `impronte:`, `impronte ->`, `impronte =`). Which print to keep, re-key or remove per (VoceRef, Parte) is
    # decided by Parlante's methods. Comments removed by lib/senza-commenti.awk; build dirs excluded. FAIL when comandi/ or politiche/ is
    # missing. Violating fixtures: `p.impronte.filter { it.registrazioneId == r }` in a politiche file, `parlante.impronte.size` in a comandi
    # file, `Parlante::impronte` in adattatori/eventi, `with(p) { impronte.size }` in comandi; conforming: `parlante.impronte` in letture/,
    # `p.rimuoviImpronteDellaParte(r)` in politiche/, `val impronte = …` declared in comandi.
---
# 0035 — The Voci dell'Incontro: one root per `Incontro`, numbers and `segmentoId`s never reused, the Parlanti consequences per `Parte`

## Context
With the `Voce` scoped to the `Incontro` [user D-0002], a `Revisione` can join `Voce`s of different `Parte`s, and a
`Ritrascrivere` or an elimination of one `Parte` must change only that `Parte`'s `Segmento`s. ADR 0018 was built for one
`Trascritto` root per `Registrazione`, with numbers renumbered from 1 at each generation and every Parlanti row of the
`Registrazione` purged. The tactical model (`features/incontro/tactical-model.md` § Trascrizione, § Parlanti, conflicts
1, 7, 8, 9) chose one root per `Incontro` (D-0011) and never-reused ids (D-0007, D-0012). This ADR pins those choices
into the code structure and amends the ADRs that said otherwise.

## Decision

### 1. The root and its persistence port
- **`VociDellIncontro`** (`:trascrizione:dominio`, root, identity `IncontroId`) holds the `Voce` counter and one
  **`Trascritto` entity per transcribed `Parte`** (identity `RegistrazioneId`; its `Segmento`s, its own `segmentoId`
  counter). `Trascritto` keeps its name, loses root status (no repository of its own). The `Revisione` methods move to
  the root: `unisci`, `dividi`, `riassegna`, `riassegnaInBlocco`, `confermaSegmento`, plus `completaParte(registrazioneId,
  segmentiIniziali)` (first transcription or replacement, [INV-I5]) and `rimuoviParte(registrazioneId)` ([INV-I6]). Each
  returns `Esito` of its events (dev-architecture §3). A `Segmento` is addressed by `SegmentoRef`.
- **Lifecycle:** created by the first completion of any `Parte` of the `Incontro`; removed only when the `Incontro`
  ceases (ADR 0038) — never when its last `Trascritto` goes, so the counter survives ([INV-I4]).
- **Port `VociDellIncontroRepository`** (`:trascrizione:applicazione ..porte`): `trova(incontroId)`, `salva(root)`,
  `rimuovi(incontroId)`, plus read-only `trascritto(registrazioneId)` for per-`Parte` read-models (no full load for one
  `Parte`). Its SQL implementation is the **only writer** of `voci_incontro`, `voce_incontro`, `trascritto`, `voce`,
  `segmento` (ADR 0034); `salva` may rewrite only the `Parte`s whose rows changed. `trova` reads in one `LetturaCoerente`
  snapshot (ADR 0029). `TrascrittoRepository` is retired in the sweep (ADR 0033 §6).
- **Cost accepted (D-0011):** a `Revisione` on a 3 h `Incontro` loads ≈ 3 000 `Segmento`s. Revisit if it is noticeably slow.

### 2. Numbering (replaces ADR 0018 §1 "[INV-12] is scoped per Trascritto generation")
- **[INV-I4]** `Voce n` is unique in the `Incontro` and never given again there — after `unire`, after a `Ritrascrivere`
  of any `Parte`, after every transcribed `Parte` is eliminated. Every new `Voce` takes the counter. A re-transcribed
  1-part `Incontro` shows "Voce 4, 5…" after "Voce 1–3" [user D-0007]. ADR 0018's rejected option "monotonic
  numbering" is now the rule; its reason for rejection (the user would see high numbers) was weighed by the user in D-0007.
- **[INV-I16]** a `segmentoId` is never reused in its `Registrazione`, across generations: a replacement numbers the new
  `Segmento`s after every id the `Parte` ever used (`trascritto.prossimo_segmento` is monotonic, ADR 0034 §2). Reason: a
  `superato` `Riassunto` keeps `Fonte`s `(registrazioneId, segmentoId)`; a reused id would resolve to an unrelated minute.
- **ADR 0018 §1's "counters restart" and the purge of "every row keyed by an old `VoceRef`"** no longer hold. ADR 0018's
  residual-race argument now holds by construction: a stale command can never hit a new `Voce` or `Segmento` with a
  reused number.

### 3. Completion and replacement of one `Parte` ([INV-I5]; ADR 0018 §2 otherwise unchanged)
The completion transaction re-reads the root by the `Parte`'s `incontroId` (through `LettoreRegistrazione`), calls
`completaParte`, `salva`s, then publishes, in order: `TrascrittoSostituito` (replacement only) and
`ElaborazioneCompletata`. New `Voce`s are never joined automatically. A failed or annullata run changes nothing.

### 4. Read-only (amends ADR 0018 Amendment (b) §2: a presentation rule, S3 presenter)
While a re-run (`Ritrascrivere`) of ANY `Parte` of the `Incontro` is `in_attesa` or `in_corso`, editing on EVERY `Parte`
page of that `Incontro` is read-only, with the banner "Ritrascrizione della parte n in corso: modifiche disabilitate fino al
termine" (multi-part) or today's banner (1-part). A first transcription of a newly imported `Parte` locks nothing.
`TrascrittoView.solaLettura: RitrascrizioneInCorso(parte: Int?)?` carries it (UX § Data views).

### 5. Events (boundaries `eventi-elaborazione`, `eventi-revisione`; Published Language)
| Event | Payload (new) | Synchronous consumer | After commit |
|---|---|---|---|
| `ElaborazioneCompletata` | `registrazioneId, incontroId` | — | views, Sbobinatura of that `Parte`, `Proposta`/`PropostaTraParti` cache |
| `TrascrittoSostituito` | `registrazioneId, incontroId, vociRimosse: Set<VoceId>` | **Parlanti only** ([INV-I8b]) — **Sintesi no longer subscribes** (ADR 0037 §7) | views, `Proposta` caches |
| `TrascrittoEliminato` (new) | `registrazioneId, incontroId, vociRimosse: Set<VoceId>` | Parlanti ([INV-I8b]); published inside the deleting transaction by Trascrizione's elimination policy, only if that `Parte` had a `Trascritto` (ADR 0038) | views |
| `VociUnite` | `incontroId, sopravvissuta, rimossa` | Parlanti [INV-21] | Sbobinatura, views |
| `VoceDivisa` | `incontroId, origine, nuova, spostati: List<SegmentoRef>` | Parlanti | Sbobinatura, views |
| `SegmentoRiassegnato` | `incontroId, segmento: SegmentoRef, da, a, daRimossa, aNuova` | Parlanti | Sbobinatura, views |
| `SegmentoConfermato` | `incontroId, segmento: SegmentoRef, confermato` | — | views |
`ElaborazioneAvviata`/`Fallita`/`Annullata` keep `registrazioneId` (per-`Parte` facts; S2 reloads the whole list).

### 6. Parlanti
- **`Attribuzione`** keyed by `VoceRef(incontroId, voceId)`: one name for the person in every `Parte` [INV-17 scope].
- **[INV-I8] prints per (`Parlante`, `VoceRef`, `Parte`)**: `ConfermaAttribuzione`/`SaltaVoce` extract one print per
  `Parte` where the `Voce` has a non-empty `SorgenteImpronta` (extract outside, then one transaction; `VoceCambiata` if any
  per-`Parte` source changed — ADR 0012 (b) unchanged in shape). A print is not required for every `Parte` of the `Voce`.
- **[INV-I8b] purge by `Parte`** (`ApplicaSostituzioneTrascrittoPolitica`, name kept as in ADR 0020, KDoc lists both
  triggers): on `TrascrittoSostituito` or `TrascrittoEliminato` of `Parte` r, synchronously: remove EVERY print sourced
  from r (any `Voce`, any `Parlante`, `secure_delete` + after-commit checkpoint, ADR 0020 §3); drop the `Attribuzione` of
  each `Voce` in `vociRimosse`; a surviving `Voce` keeps its `Attribuzione`; then [INV-25]. Every purge is exact because
  prints record their `Parte`.
- **[INV-21] (revisione-policy), additions:** in `unire(A, B)` on the SAME `Parlante`, B's prints for `Parte`s where A
  has none are re-keyed onto A (stale, refreshed after commit), where both have one A's is kept; the inheritance case
  (A unattributed, B attributed) re-keys B's prints keeping their `Parte`. **And: a print whose (`Voce`, `Parte`) slice was
  emptied by a `Revisione` is removed** — it has no source left, and `impronta_vocale`'s second FK fails the COMMIT
  otherwise (ADR 0034 §2).
- **`RiallineaImpronte(incontroId)`** (was `registrazioneId`): compare-and-set UPDATE, **never INSERT** (ADR 0012 (b),
  "no resurrection", unchanged). Consequence: a `Parte` slice a `Voce` gains by `unire`/`riassegnare` gets a print only
  from a later `ConfermaAttribuzione`/`SaltaVoce` — the Galleria is thinner, never wrong. `ImpronteRiallineate` keyed by
  `incontroId`.
- **`Proposta` [INV-20]**: the `Galleria` includes the prints from the other `Parte`s of the same `Incontro`; the `Voce`'s
  transient embeddings are one per `Parte` it speaks in; a `Candidato`'s `Fascia` is the best over (slice, print) pairs.
  Cache keyed by `VoceRef`, invalidated by `incontroId`.
- **`PianoRiassegnazione` [INV-27]** (amends ADR 0019): computed over the whole `Incontro`; a reference `Parlante`'s
  target `Voce` is its lowest `voceId` in the `Incontro`; "≥ 1 `Segmento` left" is counted in the `Incontro`.
- **`EstrattoAudio` [INV-I17]**: from ONE `Parte` — where the `Voce` speaks most (tie: the earlier `Parte`), with the
  unchanged selection function; for a `Candidato`, the `Parte` of the chosen print. One file, one player load (ADR 0005).
- **"Ospite del <data>" [INV-19]**: the first `Parte`'s `DataRegistrazione` at `SaltaVoce` (via `LettoreRegistrazione.parti`).

### 7. Sbobinatura (still one pure projection per `Parte`)
- Names by `incontroId` (`LettoreNomi.nomi(incontroId)`, ADR 0033 §4); a 1-part `Incontro` renders as today [INV-24].
- **Fan-out, simplified:** an `Incontro`-keyed event (`VociUnite`, `VoceDivisa`, `SegmentoRiassegnato`,
  `AttribuzioneConfermata`, a Parlante rename via `incontriCon`) regenerates **every transcribed `Parte` of the
  `Incontro`** (`LettoreTrascritto.partiConTrascritto`). This is a superset of the model's "every `Parte` where an affected
  `Voce` has or had `Segmento`s", chosen because Sbobinatura is stateless and cannot know where a `Voce` *had* segments;
  the output of an unaffected `Parte` is byte-identical (ADR 0012), at the cost of 2–3 small file writes.
  `ElaborazioneCompletata` and `DataRegistrazioneModificata` regenerate their `Parte` only; `OraDiInizioModificata` none.

### 8. "Ritrascrivi" dialog (amends ADR 0018 §4)
- **1-part `Incontro`:** today's title and text, unchanged ([INV-I3]).
- **Multi-part:** title "Ritrascrivere la parte n di «<titolo incontro>»?"; text "La trascrizione attuale di questa
  parte resta consultabile finché la nuova non è pronta, poi viene sostituita. Le voci che compaiono solo in questa parte,
  con le loro correzioni e i loro nomi, andranno perse; le altre voci dell'incontro restano. Il riassunto dell'incontro
  diventerà superato." Buttons unchanged. *(architect default, overridable by UX.)*

### 9. Aggregate confinement (rule 9) *(added 2026-10-01, build-manifest checkpoint [user])*
| Aggregate (block) | Writes only in its adapter | Mutated only through the root | Invariant fields read only inside |
|---|---|---|---|
| `voci-dell-incontro` (tables `voci_incontro`, `voce_incontro`, `trascritto`, `voce`, `segmento`) | ADR 0034 `adr-0034-tabelle-incontro-confinate.sh` | `adr-0035-voci-mutate-dalla-radice.sh` (from `porte-trascrizione-incontro`) | `adr-0035-contatori-solo-nella-radice.sh` (`prossimaVoce`, `prossimoSegmento`; `voci`/`trascritti` are published as read copies) |
| `parlante-impronte-per-parte` (table `impronta_vocale`) | ADR 0034 `adr-0034-tabelle-incontro-confinate.sh` | `adr-0035-impronte-mutate-dalla-radice.sh` (the one exception: ADR 0012 (b)'s `aggiornaImpronta`) | `adr-0035-impronte-lette-dalla-radice.sh` |

## Rejected options
- **Two roots** (per-`Parte` `Trascritto` + per-`Incontro` assignment): D-0011 — the `Revisione` checks need intervals and
  flags of both, in one command.
- **Reusing `segmentoId`s per generation** (ADR 0018 today): a `superato` `Riassunto`'s `Fonte` could resolve to another
  minute and person (D-0012).
- **`RiallineaImpronte` inserting prints for gained slices**: breaks ADR 0012 (b)'s no-resurrection guarantee against an
  in-flight purge.
- **Precise Sbobinatura fan-out by carrying the touched `Parte`s in every event**: more payload on four events for an
  optimisation that saves one or two file writes.

## Consequences
- The trascrizione `aggregate` block is reworked into `voci-dell-incontro`; the `elaborazione` completion and the
  per-`Parte` read-models change in the sweep (ADR 0033 §6).
- ADR 0018, 0009, 0012, 0019 gain dated pointers here; ADR 0018's two checks stay valid unchanged.
- Context-map: `TrascrittoEliminato` is a new event name for the analyst's Trascrizione language.
- Enforcement: invariant tests on the root ([INV-6], [INV-8], [INV-I4], [INV-I5], [INV-I7], [INV-I16]); the FK backstops
  and the table-ownership check of ADR 0034; the prohibitions of ADR 0018 and ADR 0012 (b), unchanged. Discursive (code
  review): the purge never runs after commit; `TrascrittoSostituito` precedes `ElaborazioneCompletata`; no path renumbers.

## Amendment 2026-10-03 — §9 rules stated as enforced [I2 amendment pass, incontro D-0049]
- **`parlante-impronte-per-parte`, mutated only through the root.** `ParlanteRepository` has a **closed method set**:
  `trova`, `delProgetto`, `nomeAttivoInUso`, `impronteDiRegistrazione`, `impronteDelProgetto` (reads), `salva`, `rimuovi`
  and `aggiornaImpronta` (ADR 0012 (b)'s compare-and-set, still the one write besides the root's). Any other method is an
  amendment of this ADR: the check is an allow-list, not a list of forbidden prefixes. A print is also not built by
  `ImprontaVocale.copy(…)` or `::ImprontaVocale` outside the domain and the mapping.
- **`parlante-impronte-per-parte`, invariant fields read only inside.** The read of `Parlante.impronte` is confined to
  `..applicazione.letture` and the persistence mapping — every other package of `:parlanti:applicazione` and
  `:parlanti:adattatori` (subscribers in `eventi`/`porte` included), also through an implicit receiver. This is wider than
  the 2026-10-01 text (comandi/ and politiche/ only); the §9 table row is read with this scope.
- `voci-dell-incontro`'s mutation check (`adr-0035-voci-mutate-dalla-radice.sh`) is unchanged: clause 3 still names
  forbidden prefixes for `VociDellIncontroRepository`.
