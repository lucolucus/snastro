---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0021 §3 (Sintesi ports re-keyed + new Progetto → Sintesi reader), architecture.md (kernel row, Progetto rows, boundaries), the kernel Published Language (VoceRef re-keyed, IncontroId and SegmentoRef added)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the user's D-0001, D-0002, D-0008, D-0009, D-0016, D-0018, D-0019 and the tactical model's seam granularity
amended: 2026-10-01   # build-manifest checkpoint [user]: §6 two blocks in sequence (persistenza-incontro then incontro-chiavi); rule-9 confinement checks below (§7). Later the same day [user, incontro D-0031]: §4.1 minimal Incontro → Parti reads brought forward into incontro-chiavi; wave-4 port blocks widen them
enforced_by:
  - check: architettura-test/controlli-adr/adr-0033-incontro-mutato-dalla-radice.sh
    from: incontro
    # PROHIBITION (§7, state mutated only through the root): (1) in progetto/dominio/src/main no `var` declaration (any visibility,
    # backing `_` names included) of incontroId, progettoId; (2) the class `Incontro` (the file of progetto/dominio declaring
    # `class Incontro`) declares no `var` at all; (3) the interfaces `IncontroRepository` and `RegistrazioneRepository` (files of
    # progetto/applicazione/src/main declaring them, found by content) declare no `fun` named aggiorna*/modifica*/imposta*/sposta*/
    # cambia*/scrivi* — their only writes are salva(<root>) and rimuovi(<id>). Comment lines stripped. FAIL when progetto/dominio or
    # progetto/applicazione is missing, or when no file declares `class Incontro` (target missing). Violating fixtures: a `private var
    # _incontroId`, a `var progettoId` in Incontro, `fun aggiornaOraDiInizio(` in RegistrazioneRepository, `fun impostaIncontro(` in
    # IncontroRepository; conforming: `private var _oraDiInizio` in Registrazione, the forbidden names only in KDoc.
  - check: architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh
    from: incontro
    # PROHIBITION (§7, invariant fields read only inside the aggregate — [INV-I2]): in every */src/main *.kt OUTSIDE progetto/dominio and
    # progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/, no line where `oraDiInizio` or `aggiuntaAlle` appears
    # together with sortedBy/sortedWith/sortedByDescending/compareBy/thenBy/thenByDescending/compareTo/maxBy/minBy (the order of the Parti is
    # decided only by OrdineDelleParti). Displaying them is allowed. Comment lines stripped. FAIL when progetto/dominio is missing.
    # Violating fixtures: `parti.sortedBy { it.oraDiInizio }` in sintesi/applicazione, `compareBy({ it.dataRegistrazione }, { it.aggiuntaAlle })`
    # in ui; conforming: the same in progetto/dominio, `Text(parte.oraDiInizio …)` in ui.
---
# 0033 — The `Incontro` in Progetto: the aggregate, the import command, and the keys and ports that cross contexts

## Context
Feature `incontro` makes the `Incontro` the unit of the `Voce`s and of the `Riassunto`, while the `Registrazione`
stays the unit of processing and of the `Sbobinatura` (context-map, amended 2026-09-30; `features/incontro/tactical-model.md`,
§ Seam granularity). Today every cross-context key is a `registrazioneId`: `VoceRef = (registrazioneId, voceId)`
(kernel), every reader port of Parlanti, Sbobinatura and Sintesi, and the Sintesi rows. This ADR fixes where the
`Incontro` lives, how a `Registrazione` gets into one, and the shape of every boundary after the re-keying. The
persistence is ADR 0034; Trascrizione and Parlanti rules are ADR 0035; the Riassunto is ADR 0037; deletion is ADR 0038.

## Decision

### 1. Placement (amends `architecture.md` § Module map; no new module, no new edge)
- **`:kernel`** gains two Published-Language types and changes one:
  - `IncontroId` (`@JvmInline value class`, `valore: String`), like `RegistrazioneId`;
  - `SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId)`: a `Segmento` across contexts. A
    `segmentoId` is unique only within its `Registrazione`, so a pair is the only exact key once a `Revisione` or a
    `Riassunto` spans `Parte`s;
  - **`VoceRef(incontroId: IncontroId, voceId: VoceId)`** replaces `VoceRef(registrazioneId, voceId)` [user D-0002].
    `voceId` is the "Voce n" number, unique in the `Incontro` and never reused there ([INV-I4], ADR 0035). The
    uniqueness that makes it a key is owned by the Voci dell'Incontro root ([INV-I4] + its invariant test).
  - `EstrattoRef` is **unchanged**: an extract is always taken from ONE `Parte` ([INV-I17]), so its
    `registrazioneId` already says which. The "estratto · parte n" label is derived by the S3 presenter from
    `TrascrittoView.parti` (counter-proposal to the UX note "EstrattoRef gains numeroParte").
- **`:progetto:dominio`** (`snastro.progetto.dominio`):
  - `Incontro` root — identity `IncontroId`, `progettoId`, both immutable; nothing else. Order, numero della parte,
    title, date, duration and "· N parti" are **derived at read time, never stored** [map];
  - `Registrazione` (amended) — gains `incontroId` (set at creation, immutable, [INV-I1]) and
    `oraDiInizio: OraDiInizio?` with `modificaOraDiInizio(ora: OraDiInizio?)` (same value → no event);
  - `OraDiInizio` VO — a local wall-clock time of day to the second, in `[00:00:00, 24:00:00)` ([INV-I14]); absent =
    unknown; no time zone;
  - `OrdineDelleParti` — the pure total order of [INV-I2]: (`DataRegistrazione`, `OraDiInizio` with **empty last**,
    `aggiuntaAlle`, `registrazioneId`); numero della parte = 1-based rank. Table test. **The only place the order is
    computed**: every other context receives it through its port, already ordered and numbered.
- **`:progetto:applicazione`**: commands `AggiungiRegistrazione` (amended, §2), `ModificaOraDiInizio` (new),
  `ModificaDataRegistrazione` and `EliminaRegistrazione` (events amended, §3, ADR 0038); port `IncontroRepository`
  (`trova`, `salva`, `rimuovi`, `partiDi(incontroId)`); read-model `IncontriDelProgetto` (§5) replacing
  `RegistrazioniDelProgetto` as S2's list; the public query API `CatalogoRegistrazioni` gains
  `incontro(id): IncontroVista?` (the `Incontro` with its `Parte`s ordered and numbered by `OrdineDelleParti`) and
  `RegistrazioneVista.incontroId` / `oraDiInizio`.
- **`SondaAudio`'s `InfoAudio`** gains `oraDiInizio: LocalTime?`. The reading rule is spike `ora-di-inizio`'s ADR;
  **until it closes, the adapter returns `null`** and the import stores an empty `OraDiInizio`. *(2026-10-01: closed by
  [ADR 0040](0040-data-e-ora-da-udta-date.md) — `moov/udta/date`, the same instant also giving `DataRegistrazione`;
  empty otherwise.)* Only that adapter
  change is blocked by the spike; the VO, the order, `ModificaOraDiInizio`, the migration and the import are not.
- **The "Voci dell'Incontro" root** lives in `:trascrizione:dominio` (ADR 0035). `Parte` gets **no type**: it is a role
  of `Registrazione` [map].

### 2. Import: ONE command, three destinations [user D-0008, D-0016, D-0019]
```kotlin
public data class AggiungiRegistrazione(
    val progettoId: ProgettoId,
    val file: List<String>,               // ≥ 1 source paths, in the order the user selected them (amended 2026-10-02, D-0045)
    val destinazione: Destinazione,
)
public sealed interface Destinazione {
    public data object NuovoIncontro : Destinazione          // every file is a Parte of ONE new Incontro
    public data object IncontriSeparati : Destinazione       // one new 1-part Incontro per file ("N incontri separati")
    public data class Incontro(val incontroId: IncontroId) : Destinazione   // "Aggiungi parti…"
}
```
- **One command, not N**, also for "N incontri separati". The UX makes every multi-file import **all or nothing**
  ("Nessun file importato: «<file>» non è leggibile."); one command gives that with one transaction, while N commands
  would need a compensation across committed commands. A single file from S2 is `NuovoIncontro`.
- **Shape of the run:** probe every file (`SondaAudio`), copy every file (`ArchivioAudio.copia`), all **outside** the
  transaction (ADR 0012); then ONE transaction: re-read the target `Incontro` (`Destinazione.Incontro` of another
  `Progetto` or absent → `IncontroNonTrovato`, nothing written), create the new `Incontro`(s), insert one
  `Registrazione` per file with its `incontroId`, its `titolo` (unchanged rules; uniqueness also among the files of
  the same import) and its `OraDiInizio` from `InfoAudio`. Any failure → nothing written, every copy made is
  `scartata` (the existing compensation, CR-7). No `Elaborazione` starts (ADR 0014).
- `BEGIN IMMEDIATE` serializes it against `EliminaRegistrazione`: an import into an `Incontro` whose last `Parte` was
  deleted first answers `IncontroNonTrovato` ([INV-I1]: an empty `Incontro` never exists).

### 3. Events (boundary `eventi-progetto`, Published Language; all additive except where noted)
| Event | Payload | Delivery |
|---|---|---|
| `RegistrazioneAggiunta` | `+ incontroId` | after commit, one per file |
| `DataRegistrazioneModificata` | `+ incontroId` | after commit |
| `OraDiInizioModificata` (new) | `registrazioneId, incontroId, precedente: LocalTime?, nuova: LocalTime?` | after commit |
| `RegistrazioneEliminata` | `+ incontroId, incontroCessato: Boolean` | in-transaction, ADR 0038 |

No "Incontro creato" event: its only reader would be the list refresh that `RegistrazioneAggiunta` already drives.

### 4. Boundaries after the re-keying (each a consumer-owned port + consumer-driven contract, ADR 0002)
The **single home** of these shapes. Every port keeps its `<Porta>Contratto` + `<Porta>Finta` (fake runs it in the
gate, the real adapter runs it seeded through the supplier's commands, dev-architecture `#porta-contratto`).

| Boundary | Consumer port (owner) | Supplier | Pinned shape |
|---|---|---|---|
| Progetto → Trascrizione | `LettoreRegistrazione` (Trascrizione) | `CatalogoRegistrazioni` | `registrazione(id)` view `+ incontroId`; **new** `parti(incontroId): List<ParteDiIncontro(registrazioneId, numero)>?` ordered by [INV-I2], `null` = unknown `Incontro` |
| Progetto → Parlanti | `LettoreRegistrazione` (Parlanti) | `CatalogoRegistrazioni` | `registrazione(id)` `+ incontroId`; **new** `parti(incontroId): List<ParteDiIncontro(registrazioneId, numero, dataRegistrazione)>?` — "Ospite del <data>" = first `Parte`'s date ([INV-19]); `EstrattoAudio` tie = earlier `Parte` ([INV-I17]) |
| Progetto → Sintesi | **new** `LettoreIncontro` (Sintesi) | `CatalogoRegistrazioni` | `parti(incontroId): List<ParteSintesi(registrazioneId, numero)>?` ordered; `null` = the `Incontro` no longer exists |
| Trascrizione → Parlanti | `LettoreVoci` | `VociDelTrascritto` (re-keyed) | `voci(incontroId): List<VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>)>?`; `segmenti(incontroId): List<SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)>?`; `null` = no transcribed `Parte` |
| Trascrizione → Sbobinatura | `LettoreTrascritto` (Sbobinatura) | `VociDelTrascritto` | `trascritto(registrazioneId): TrascrittoTesto?` `+ incontroId`; **new** `partiConTrascritto(incontroId): List<RegistrazioneId>`; `registrazioniConTrascritto()` unchanged |
| Parlanti → Sbobinatura | `LettoreNomi` (Sbobinatura) | `NomiDelleVoci` | `nomi(incontroId): Map<VoceRef, String>`; `incontriCon(p: ParlanteId): List<IncontroId>` (replaces `registrazioniCon`) |
| Trascrizione → Sintesi | `LettoreTrascritto` (Sintesi) | `VociDelTrascritto` + `StatiElaborazione` | `segmenti(registrazioneId)` unchanged; `elaborazioneAperta` replaced by `statoParte(registrazioneId): StatoParteSintesi` = `DA_TRASCRIVERE` (no `Trascritto`, no run) \| `IN_TRASCRIZIONE` (any run open) \| `NON_RIUSCITA` (no `Trascritto`, latest run `fallita`) \| `TRASCRITTA` (a `Trascritto`, no run open) — the three disabled hints of D-0020 |
| Parlanti → Sintesi | `LettoreNomi` (Sintesi) | `NomiDelleVoci` | `nomi(incontroId): Map<VoceRef, String>`, attributed only. **"Voce no longer present" is NOT asked to Parlanti** (counter-proposal to the tactical note): Sintesi derives presence from the current structure it already reads through `LettoreTrascritto` (ADR 0037 §6) — a removed `Voce` has no `Attribuzione` anyway, so the names port cannot tell it apart, and Voce existence is Trascrizione's fact |

#### 4.1 The minimal reads that `incontro-chiavi` brings forward *(amended 2026-10-01 [user, incontro D-0031])*
The sweep (§6 step 2) removes every wave-1 join, but three paths must still go from an `Incontro` to its `Parte`s, before
the ordered reads of the table above exist (wave 4). Option A of the bounced `incontro-chiavi`: **one minimal method per
boundary, owned by block `incontro-chiavi`**; the wave-4 port blocks then **widen** these same methods to the shapes in
the table above, and never add a parallel one.

| Boundary | Method (pinned now) | Published Language | Widened in wave 4 by | to |
|---|---|---|---|---|
| Progetto public query (supplier) | `CatalogoRegistrazioni.parti(incontroId: IncontroId): List<RegistrazioneId>?` | kernel ids only | `catalogo-incontro` | `incontro(id): IncontroVista?` (ordered, numbered); `parti` stays as its projection |
| Progetto → Sintesi | **`LettoreIncontro.parti(incontroId: IncontroId): List<RegistrazioneId>?`** (new Sintesi port) | kernel ids | `porte-sintesi-incontro` | `List<ParteSintesi(registrazioneId, numero)>?`, ordered |
| Progetto → Parlanti | **`LettoreRegistrazione.parti(incontroId: IncontroId): List<RegistrazioneId>?`** (Parlanti's port) | kernel ids | `porte-parlanti-incontro` | `List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>?`, ordered |
| Progetto → Trascrizione, Parlanti, Sintesi | `RegistrazioneVista.incontroId: IncontroId` on each consumer's own view of `registrazione(id)` | kernel id | — (final) | — |

Rules of these minimal reads:
- **Unordered.** The list's order carries no meaning: the order of the `Parte`s belongs to the `incontro` aggregate
  (wave 3, [INV-I2]). No consumer sorts it or relies on its order. `null` = unknown `Incontro`, or one that ceased (its last
  `Parte` was deleted); a known `Incontro` always has ≥ 1 element ([INV-I1]).
- **Parlanti, VoceRef → its `Parte`s:** a `Parlanti` command or read-model holding a `VoceRef(incontroId, voceId)` reads
  `LettoreRegistrazione.parti(incontroId)`, then the `Voce`s of each `Parte` through its existing per-`Registrazione`
  `LettoreVoci.voci(r)` (whose `VoceRef`s now carry the `incontroId`), and keeps the `Parte`s where that `voceId` speaks;
  audio is decoded per `Parte` with the existing `DecodificatoreAudio.campioni(r, …)`. No new Trascrizione method in wave 2.
- **Sintesi, run and view:** `Riassumi`, `EseguiProssimoRiassunto` and `riassunto-vista` (keyed by `incontroId`) read
  `LettoreIncontro.parti(incontroId)` and then the existing per-`Parte` `LettoreTrascritto.segmenti(r)` /
  `elaborazioneAperta(r)`.
- **Trascrizione: the services resolve, the repository never joins.** Every service and public query that starts from a
  `registrazioneId` reads `LettoreRegistrazione.registrazione(r).incontroId` first, in its own transaction or snapshot. The
  `Trascritto` (still the root in wave 2) carries its `incontroId`. `TrascrittoRepository.trova(r: RegistrazioneId, incontroId:
  IncontroId)` and `rimuovi(r, incontroId)` receive it, and `salva(t)` writes `voci_incontro` / `voce_incontro` with
  `t.incontroId`. No Trascrizione query reads the `registrazione` table. In wave 3 `VociDellIncontroRepository.trova(incontroId)`
  replaces this.
- **Behaviour-neutral:** this holds while every `Incontro` has one `Parte` (§6). With one element, "unordered" cannot
  change any result.
- **D-0028 is kept:** no repository UPDATE names `incontro_id` (ADR 0034 §2).
- **Contract tests** (dev-architecture `#porta-contratto`; Contratto + Finta in each consumer's `testFixtures`; the real
  adapter runs the same Contratto seeded through Progetto's commands):
  - `LettoreIncontroContratto` + `LettoreIncontroFinta` (`:sintesi:applicazione`), real `LettoreIncontroDaProgetto`
    (`:sintesi:adattatori ..porte`). It asserts: the set of the `Incontro`'s `Registrazione`s; `null` for an unknown id;
    `null` after its only `Parte` is deleted; a `Registrazione` of another `Incontro` is never listed. No order is asserted.
  - `LettoreRegistrazioneContratto` (Parlanti) gains the same four cases for `parti`. It also asserts that
    `registrazione(r).incontroId` equals the one set at import.
  - `LettoreRegistrazioneContratto` (Trascrizione) asserts the same about `registrazione(r).incontroId`.
  - Supplier test on `CatalogoRegistrazioni.parti` (`:progetto:applicazione`), with the same cases.

Cross-context events re-keyed by Trascrizione and Parlanti are listed in ADR 0035 §5; Sintesi's in ADR 0037 §1.
**Authorship:** reads consumer-driven, writes producer-driven, as before. Progetto still depends on no context.

### 5. Read-models per owning context (R1) — counter-proposal to the UX's single `IncontriDelProgetto`
The UX `IncontriDelProgetto` view mixes three contexts. Per architecture.md R1 it is served by one read-model per
owner, joined by `incontroId`/`registrazioneId` in the S2 presenter:
- **Progetto `IncontriDelProgetto`**: `incontroId`, derived `titolo`/`data`/`durataMs`/`numParti`, and `parti`
  (`registrazioneId`, `numero`, `titolo`, `dataRegistrazione`, `oraDiInizio?`, `durataMs`), newest first by the
  `Incontro`'s date;
- **Trascrizione**: `stati-elaborazione` per `Registrazione` (unchanged) + **new** `VociIncontro` per `incontroId`
  (`voceId`, `etichetta`, `parti: [Int]`, `numVoci`) + `numeroPersonePrecompilato(incontroId)` (ADR 0039);
- **Parlanti**: `identificazione-incontri` (`numVociDaIdentificare` per `incontroId`, replacing the per-`Registrazione`
  one) and `nome?` for `VociIncontro`;
- **`:avvio` `PosizioniNellaCoda`** for "In coda · n" (ADR 0023 §4);
- the **aggregated state** (priority 1–5 of the UX) is a pure function of the per-`Parte` states in the S2
  presenter: presentation, no domain rule.

### 6. Breaking change: two blocks in sequence, no coexistence of keys *(amended 2026-10-01, build-manifest checkpoint [user])*
`VoceRef`'s re-key and the per-`Registrazione` reader ports are breaking for every consumer. Strategy, fixed before
any block, in **two blocks in sequence**:
1. **`persistenza-incontro` (wave 1)** lands `7.sqm` under the CURRENT domain types. Its repositories resolve the new
   `incontro_id` keys from `registrazione_id` **by SQL join** (reading the `registrazione.incontro_id` column, never
   assuming `incontro.id = registrazione.id`), and a newly saved `Registrazione` gets its own new `incontro` row.
   **Condition:** this is sound only while **every `Incontro` has exactly one `Parte`** — true for migrated data (ADR 0034)
   and for every import until the multi-file import into an `Incontro` (block `aggiungi-registrazione-incontro`,
   release I2) exists. No block that can give an `Incontro` a second `Parte` may land before step 2.
2. **`incontro-chiavi` (wave 2)** is the compile-checked sweep: it changes the kernel types and every use at once, the
   repositories write and read the `incontro_id` columns directly, and the wave-1 joins go. Where a path must go from
   an `Incontro` to its `Parte`s, it uses the minimal unordered reads of §4.1, which this block owns *(amended 2026-10-01
   [user, incontro D-0031])*. In Trascrizione the services resolve `incontroId` through `LettoreRegistrazione` and pass it
   to the repository.

On existing data both steps are behaviour-neutral. No deprecated symbol survives step 2, so no `cleanup` node is
needed. After it, published types evolve additively.

### 7. Aggregate confinement (rule 9) *(added 2026-10-01, build-manifest checkpoint [user])*
For the `incontro` aggregate (block `incontro`; `invariant_fields`: `Registrazione.incontroId`, `Registrazione.oraDiInizio`,
`Incontro.progettoId`; tables `incontro`, `registrazione`):
- **persistent writes only in its adapter** — `incontroQueries` / `registrazioneQueries` used in `src/main` only in
  `snastro.progetto.adattatori.persistenza`: ADR 0034's `adr-0034-tabelle-incontro-confinate.sh` (clause 1);
- **state mutated only through the root** — `adr-0033-incontro-mutato-dalla-radice.sh` (above);
- **invariant fields read only inside the aggregate** — the decision they carry, the order of the `Parte`s, is made only
  in `:progetto:dominio`: `adr-0033-ordine-solo-nel-dominio.sh` (above). `incontroId` itself is Published Language (§4):
  readers use it as a key and never change it (clause 1 of the mutation check).

## Rejected options
- **N `AggiungiRegistrazione` for "N incontri separati"**: all-or-nothing would need cross-command compensation.
- **`Incontro` as a column-only concept (no table, `incontro_id` a free UUID on `registrazione`)**: the `Voce`s, the
  `Attribuzione`s and the `Riassunto` would reference nothing, and no FK could fail closed (ADR 0034).
- **A `Parte` type**: the map forbids it (a role, not an entity).
- **Storing the order or the numero della parte**: an edit of date/time or an import would have to rewrite rows of
  other `Registrazione`s; the map says derived.
- **Each consumer computing the order from dates**: three copies of [INV-I2]; one would drift.
- **Keeping `VoceRef(registrazioneId, …)` and adding an `IncontroVoceRef`**: two keys for one identity, the purge rules
  would have to translate.

## Consequences
- `architecture.md` gains a dated amendment (kernel row, Progetto rows, boundaries pointer); ADR 0021 §3 gains a pointer.
- `CatalogoRegistrazioni` stays a plain projection: `OrdineDelleParti` is a domain function it calls, not a rule it owns.
- The context-map needs no change for the shapes; the event `OraDiInizioModificata` and `TrascrittoEliminato`
  (ADR 0035) are new names the analyst may add to the ubiquitous language.
- Enforcement: structural (compile + module graph). Discursive (code review): no context but Progetto computes the
  order of the `Parte`s; every multi-file import is one transaction; no file I/O inside it.

## Amendment 2026-10-02 — `AggiungiRegistrazione.file` is `List<String>` [user, incontro D-0045]
§2 pinned `file: List<Path>` (`java.nio.file.Path`), which contradicts [ADR 0002](0002-esagonale-modulo-per-contesto.md)'s
check and rule CR-2 (no `java.nio.file.` in any `*/applicazione`). The command carries the source paths as `String`,
as `percorsoSorgente`, `SondaAudio` and `ArchivioAudio` already do; the adapters turn them into `Path`. No exception
is added to ADR 0002.
