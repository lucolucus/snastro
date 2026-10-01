---
scope: global
status: accepted
supersedes: null
amended: 2026-09-27   # see §4 dated note 2026-09-27 (reads in a LetturaCoerente snapshot, ADR 0029)
closes_spike: null
enforced_by:   # migrated 2026-09-26 (mismAgent 0.22) from the legacy inline shell rule: same grep/find logic, now versioned checks run by the gate (architettura-test ControlliAdrTest, red-green on fixture/<check>/)
  - check: architettura-test/controlli-adr/adr-0022-schema-sintesi.sh
    from: persistenza-sintesi
  - check: architettura-test/controlli-adr/adr-0022-riassunto-senza-parlanti.sh
    from: persistenza-sintesi
  # legacy note: block id proposed here, pinned by build-manifest. Validated 2026-09-25 via bash -c: tree exit 2 (6.sqm absent — red BY DESIGN until that block); fixtures: PASS on a 6.sqm with the table, the immediate FK line, both indexes each on one line, impostazioni_sintesi, and `nome`/`parlante_id` only in `--` comments; FAIL when the pronto index is commented out, when the non-pronto index omits 'fallito', when the FK is removed, on a `responsabile_nome` column, on a `parlante_id` column
---
# 0022 — Sintesi persistence: `6.sqm`, one row per element and per Fonte, the structure as canonical text, INV-S2/S3 as partial unique indexes

## Context
The tactical model leaves these to the architect:
- the `Riassunto` tables: plain TEXT, one row per element and per `Fonte`, no JSON blob, so a later
  FTS5 index stays possible (brief: full-text search is a later feature);
- how the `segmentoId → voceId` structure a `Riassunto` was made from is kept (full rows or a
  digest): the basis of `superato`, [INV-S7];
- the per-`Riassunto` cap column ([INV-S10]);
- the per-`Progetto` lunghezza massima ([INV-S9]): a table or a column;
- the store backstops of [INV-S2] and [INV-S3].

The trunk rules apply:
- ADR 0006: SQLDelight, one DB per Progetto, forward-only `.sqm`, and migrations are the schema;
- ADR 0007: set rules as partial unique indexes, each on one line;
- ADR 0012: one transaction per command, `BEGIN IMMEDIATE`.

`5.sqm` is ADR 0020's `eliminazione_in_sospeso`, so Sintesi's migration is **`6.sqm`**
(schema 6 → 7).

## Decision

### 1. Schema (`persistenza/src/main/sqldelight/migrations/6.sqm`)
```sql
-- Aggregate: Riassunto (Sintesi, ADR 0022). Content columns are non-NULL iff stato = 'pronto' (INV-S1).
-- Speakers only as {V<n>} tokens inside TEXT and as voce_id integers (INV-S5).
CREATE TABLE riassunto (
    id TEXT NOT NULL PRIMARY KEY,
    registrazione_id TEXT NOT NULL REFERENCES registrazione(id),
    stato TEXT NOT NULL CHECK (stato IN ('in_attesa', 'in_corso', 'pronto', 'fallito')),
    argomento TEXT,                                  -- NULL = no Argomento
    lunghezza_massima_parole INTEGER NOT NULL CHECK (lunghezza_massima_parole > 0),  -- the cap requested with (INV-S10); range lives in the VO
    richiesto_alle INTEGER NOT NULL,                 -- Instant, epoch millis — FIFO key of the shared queue (ADR 0023)
    avviato_alle INTEGER,                            -- NULL until in_corso
    motivo_fallimento TEXT,                          -- canonical code, NOT NULL iff fallito
    sommario TEXT,                                   -- NULL when absent or dropped by the Verifica
    omessi INTEGER,                                  -- NOT NULL iff pronto
    struttura TEXT,                                  -- canonical segmentoId:voceId encoding, NOT NULL iff pronto
    CHECK ((stato = 'fallito') = (motivo_fallimento IS NOT NULL)),
    CHECK ((stato = 'pronto') = (struttura IS NOT NULL AND omessi IS NOT NULL)),
    CHECK (stato = 'pronto' OR sommario IS NULL)
);

CREATE INDEX riassunto_registrazione ON riassunto(registrazione_id);

CREATE UNIQUE INDEX riassunto_non_pronto_unico ON riassunto(registrazione_id) WHERE stato IN ('in_attesa', 'in_corso', 'fallito');

CREATE UNIQUE INDEX riassunto_pronto_unico ON riassunto(registrazione_id) WHERE stato = 'pronto';

-- Elements of a pronto Riassunto, one row each; posizione = order inside its tipo list.
CREATE TABLE riassunto_elemento (
    riassunto_id TEXT NOT NULL REFERENCES riassunto(id),
    tipo TEXT NOT NULL CHECK (tipo IN ('decisione', 'questione_aperta', 'azione', 'punto_chiave')),
    posizione INTEGER NOT NULL,
    testo TEXT NOT NULL,                             -- plain text with {V<n>} tokens (FTS-ready)
    voce_id INTEGER,                                 -- Responsabile (azione) / speaker (punto_chiave); NULL otherwise or unbound
    PRIMARY KEY (riassunto_id, tipo, posizione),
    CHECK (voce_id IS NULL OR tipo IN ('azione', 'punto_chiave'))
);

-- One row per Fonte (a SET per element: the PK forbids duplicates).
CREATE TABLE riassunto_fonte (
    riassunto_id TEXT NOT NULL,
    tipo TEXT NOT NULL,
    posizione INTEGER NOT NULL,
    segmento_id INTEGER NOT NULL,
    PRIMARY KEY (riassunto_id, tipo, posizione, segmento_id),
    FOREIGN KEY (riassunto_id, tipo, posizione) REFERENCES riassunto_elemento(riassunto_id, tipo, posizione)
);

-- Per-Progetto setting owned by Sintesi (INV-S9). No row = the default (2000 parole).
CREATE TABLE impostazioni_sintesi (
    progetto_id TEXT NOT NULL PRIMARY KEY REFERENCES progetto(id),
    lunghezza_massima_riassunto_parole INTEGER NOT NULL CHECK (lunghezza_massima_riassunto_parole > 0)
);
```
Queries go in `.sq` files named `Riassunto.sq`, `RiassuntoElemento.sq`, `RiassuntoFonte.sq`,
`ImpostazioniSintesi.sq` (generated `riassunto*Queries` / `impostazioniSintesiQueries`, the names
ADR 0021's prohibition relies on).

### 2. Choices, with the reason for each
- **Elements and `Fonte`s as rows, never a JSON blob.** `testo` and `sommario` are plain TEXT, so a
  future FTS5 external-content table can index `riassunto.sommario` + `riassunto_elemento.testo`
  without a data migration. The `{V<n>}` tokens stay in the text. The future FTS feature decides
  whether to index a rendered copy instead.
- **Speaker tokens: `{V<n>}`, literal braces doubled.** `TestoConVoci` (`:sintesi:dominio`) owns the
  lossless codec (round-trip VO test). It is the same form the `ModelloLinguistico` port returns
  (ADR 0021 §4). There is one form end to end, and the prompt syntax never reaches storage.
- **The structure is ONE canonical TEXT column (`struttura`), not rows and not a hash.**
  - Encoding: `"<segmentoId>:<voceId>"` pairs, ordered by `segmentoId`, joined by `,`
    (e.g. `"1:1,2:2,3:1"`), produced by `StrutturaTrascritto.chiave` in the domain.
  - It follows the precedent of `SorgenteImpronta.chiave` (ADR 0012 (b)): exact, collision-free, no
    hashing in the domain, one equality test.
  - `superato` ([INV-S7]) = `StrutturaTrascritto.di(current segmenti).chiave != struttura`.
  - A `Revisione` that restores the assignment clears it by construction.
  - Size: about 1 000 `Segmento`s per hour, about 8 KB, once per `pronto` row.
  - Full rows were rejected: about 1 000 rows per `Riassunto` for a question ("does it differ?")
    that needs only equality, and no reader needs a per-segment diff in v1.
  - The run-time Verifica ([INV-S4]) uses the structure **in memory**, as read for that run. It
    never reads it back from storage.
- **The cap is a column on the `Riassunto`** (`lunghezza_massima_parole`): it is fixed at request
  ([INV-S10]) and shown in the metadata line. The **range** [300, 2500] is not a SQL `CHECK`: it is
  provisional (spikes `runtime-llm-in-app`, `qualita-riassunto`), and a forward-only migration would
  have to rebuild the table to change it. The VO owns it. SQL keeps only `> 0`.
- **The per-`Progetto` setting is a Sintesi-owned table, not a column on `progetto`.** `progetto` is
  Progetto's table, and Sintesi writing it would break ownership (ADR 0021 §8). The key is
  `progetto_id`, with an FK to the one `progetto` row of this per-project DB (rows are never
  deleted). With no row, the root reconstitutes the default (2000), so there is nothing to backfill.
- **`motivo_fallimento` is a canonical code, not display text:**
  - `modello_non_disponibile`
  - `errore_modello`
  - `troppo_lunga`
  - `nessun_contenuto_verificabile`
  - `interrotto`
  
  `:ui` maps each code to the Italian text. There is no SQL `CHECK`, because the list may grow with
  spike `contesto-lungo`. The `StatoRiassunto`/`MotivoFallimento` enums are the guard.
- **Other context tables:**
  - The only FK to another context is `riassunto.registrazione_id → registrazione(id)`,
    **IMMEDIATE**. It is deliberate: deleting a `registrazione` with a `Riassunto` left fails the
    delete, so a composition that forgot Sintesi's subscriber fails **closed** (ADR 0024).
  - **No FK to `segmento`/`voce`.** `TrascrittoRepository.salva` deletes and re-inserts those rows
    on every `Revisione` (ADR 0018), and Sintesi must not couple to Trascrizione's tables.
    `segmento_id`/`voce_id` are Published-Language integers, checked by the Verifica at run time.
- **Child rows** are deleted explicitly by `RiassuntoRepositorySql`, never by a cascade
  (dev-architecture `#repository`): `rimuovi` deletes `riassunto_fonte` → `riassunto_elemento` →
  `riassunto`.

### 3. Set invariants on the store (ADR 0007 pattern)
- **[INV-S2] + [INV-S3] (non-`pronto` half)** → `riassunto_non_pronto_unico`: per `Registrazione`,
  at most one row that is `in_attesa | in_corso | fallito`. `Riassumi` removes a previous `fallito`
  in its own transaction **before** inserting, so on the normal path only an **open** row can
  collide. The repository maps the violation to `ErroreSintesi.RiassuntoGiaAperto`. The service
  checks first (ADR 0007), and the index is the backstop.
- **[INV-S3] (`pronto` half)** → `riassunto_pronto_unico`: at most one `pronto`. The completion
  removes the previous `pronto` before it promotes the new one (§4).

### 4. Repository port (`RiassuntoRepository`) — the operations the commands need
- `trova(id)`, `diRegistrazione(registrazioneId): List<Riassunto>`, `inAttesa(): List<Riassunto>`
  (FIFO by `(richiesto_alle, id)`), `inCorso(): List<Riassunto>`.
- `salva(r)`: upsert of the root + replace of its children, always inside the caller's transaction.
- `rimuovi(id)`, `rimuoviDiRegistrazione(registrazioneId): Int` (the count, used by the policies).
- **Completion is a compare-and-set** ([INV-S8]). Inside one transaction (`BEGIN IMMEDIATE`, so
  the re-read is authoritative):
  1. re-read the row by id. If it is absent or not `in_corso`, write nothing, publish nothing, and
     return Ok with no effect;
  2. only for `pronto`, remove the previous `pronto` of that `Registrazione`;
  3. `UPDATE riassunto SET … WHERE id = :id AND stato = 'in_corso'` + insert the children (the SQL
     condition is the defensive half);
  4. publish `RiassuntoPronto` / `RiassuntoFallito`.
  
  A `fallito` never touches the shown `pronto` ([INV-S3]).
- The fake (`RiassuntoRepositoryFinta`) honours both indexes, like the other repository fakes. The
  `RiassuntoRepositoryContratto` runs against the fake and against the SQL implementation
  (round-trip, index → `Esito` mapping, a concurrent-insert case, the CAS race cases).
- *(2026-09-27, [ADR 0029](0029-lettura-coerente-deferred.md) [user])* `trova`, `diRegistrazione` and every read that
  loads a `Riassunto` with its children run inside `LetturaCoerente.inLettura` (BEGIN DEFERRED, read-only snapshot),
  received by the repository's constructor. The row and its children are never read from two different snapshots.
  The SQL subclass of `RiassuntoRepositoryContratto` gains the concurrency case of ADR 0029 §5. Completion (the CAS
  above) is a write and stays `BEGIN IMMEDIATE`.

### 5. Migration test
`6.sqm` joins the `:persistenza:test` migration test unchanged (ADR 0006 (a)): an empty DB migrated
to 7, `integrity_check`/`foreign_key_check`, every new query run once. The same test also checks
that `Schema.migrate` from empty equals `Schema.create`. It also covers the **ADR 0020 interaction**:
an `EliminaRegistrazione` with a `riassunto` row left fails on the immediate FK, and one with the
Sintesi subscriber registered passes (in the `avvio-sintesi` e2e).

## Consequences
- **Build dependency:** `6.sqm` assumes `5.sqm` (ADR 0020, block `persistenza-elimina-registrazione`)
  is merged. That block is still being built on the sibling branch, so `persistenza-sintesi` waits
  for that merge, or is rebased onto it.
- `Schema.version` becomes 7.
- Enforcement: `enforced_by` above (presence + prohibition). It is exigible once
  `persistenza-sintesi` merges, and red by design before that. The index → error mapping and the CAS
  are covered by the repository contract. The "never edit a shipped `.sqm`" rule is CR-13 (review).

## Amendment 2026-10-01 — re-keyed by `7.sqm` ([ADR 0034](0034-persistenza-incontro-7sqm.md), [ADR 0037](0037-riassunto-dell-incontro.md) §5)
- `riassunto.registrazione_id` becomes `incontro_id` (IMMEDIATE FK to `incontro`); both partial unique indexes are on
  `incontro_id`; `riassunto_fonte` gains `registrazione_id` (no FK); `struttura` is `StrutturaIncontro.chiave`
  (`<registrazioneId>=<old encoding>` per `Parte`, joined by `;`), re-encoded by the migration so nothing becomes `superato`.
- This ADR's two checks keep reading `6.sqm`, which is never edited; the live schema is checked by ADR 0034's.
