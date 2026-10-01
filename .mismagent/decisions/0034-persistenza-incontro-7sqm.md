---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0022 §1/§3 (riassunto keyed by incontro_id, riassunto_fonte gains registrazione_id, struttura re-encoded; its 6.sqm checks stay true of 6.sqm), ADR 0007 (set rules of the riassunto re-keyed; a Voce is unique per Incontro), ADR 0018 §1 (trascritto.prossima_voce retired; prossimo_segmento never restarts), ADR 0009 (a migration copies the prints; pages zeroed + checkpoint)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the tactical model's § Migration and the user's D-0001, D-0002, D-0003, D-0007
enforced_by:
  - check: architettura-test/controlli-adr/adr-0034-schema-incontro.sh
    from: persistenza-incontro
    # PRESENCE on persistenza/src/main/sqldelight/migrations/7.sqm (comment lines `--` stripped): (1) `CREATE TABLE incontro (`;
    # (2) an `ALTER TABLE registrazione ADD COLUMN incontro_id TEXT REFERENCES incontro(id)` line; (3) `CREATE TABLE voci_incontro (`
    # with `incontro_id TEXT NOT NULL PRIMARY KEY REFERENCES incontro(id)`; (4) `CREATE TABLE voce_incontro (`; (5) a riassunto
    # table line `incontro_id TEXT NOT NULL REFERENCES incontro(id),`; (6) the two one-line indexes
    # `CREATE UNIQUE INDEX riassunto_non_pronto_unico ON riassunto(incontro_id) WHERE stato IN ('in_attesa', 'in_corso', 'fallito');`
    # and `CREATE UNIQUE INDEX riassunto_pronto_unico ON riassunto(incontro_id) WHERE stato = 'pronto';`; (7) a riassunto_fonte table
    # line `registrazione_id TEXT NOT NULL,`. FAIL if 7.sqm is missing. Violating fixtures: each clause removed or commented out
    # in turn, an index keyed on registrazione_id, the riassunto FK made DEFERRABLE, 7.sqm absent.
  - check: architettura-test/controlli-adr/adr-0034-tabelle-incontro-confinate.sh
    from: persistenza-incontro
    # PROHIBITION (amended 2026-10-01, rule 9 "persistent writes only in the aggregate's adapter", §5). Clause 1, on every *.kt under
    # */src/main except the :persistenza module (build dirs and `//`, `*`, `/*` comment lines excluded), each generated query family is
    # used ONLY under its owner's persistence package:
    #   incontroQueries, registrazioneQueries                                   → progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/
    #   vociIncontroQueries, voceIncontroQueries, trascrittoQueries, voceQueries, segmentoQueries → trascrizione/adattatori/src/main/kotlin/snastro/trascrizione/adattatori/persistenza/
    #   improntaVocaleQueries                                                   → parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/
    #   riassuntoQueries, riassuntoElementoQueries, riassuntoFonteQueries       → sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/
    # (test source sets may seed through queries). Clause 2: in 7.sqm, a `parlante_id` or `nome` column inside a `CREATE TABLE riassunto…`
    # block (INV-S5 storage, ADR 0022's rule carried to the rebuilt tables). FAIL when a target dir (progetto, trascrizione, parlanti,
    # sintesi, sbobinatura, avvio, ui) or 7.sqm is missing. Violating fixtures: `db.incontroQueries` in trascrizione/, `registrazioneQueries`
    # in progetto/adattatori/…/porte/, `segmentoQueries` in avvio/, `voceIncontroQueries` in parlanti/, `improntaVocaleQueries` in
    # parlanti/applicazione, `riassuntoFonteQueries` fully qualified in sintesi/adattatori/…/eventi/, `responsabile_nome` in
    # riassunto_elemento_v8; conforming: the names only in comments, each family in its own persistence package, a family in a
    # src/test file, `parlante_id` in the rebuilt attribuzione table of 7.sqm.
---
# 0034 — Persistence of the `Incontro`: `7.sqm`, one `Incontro` per existing `Registrazione`, `Voce`s keyed per `Incontro`, the `Riassunto` re-keyed with nothing becoming `superato`

## Context
ADR 0033 puts the `Incontro` in Progetto and re-keys `VoceRef` and the `Riassunto` by `incontroId`; ADR 0035 makes
the Voci dell'Incontro one root. The schema is `1.sqm`…`6.sqm` (version 7); the next migration is **`7.sqm`**
(7 → 8, ADR 0006 (a), forward-only, CR-13). Facts that constrain the plan:
- migrations run inside ONE transaction with `foreign_keys=ON` (`AperturaDatabase`): `PRAGMA foreign_keys` cannot be
  turned off, so **a table referenced by an immediate FK cannot be rebuilt** — `registrazione` (referenced by
  `elaborazione`, `trascritto`, `riassunto`), `trascritto` (by `voce`), `voce` (by `segmento`) stay in place;
- SQLite cannot `ADD COLUMN … NOT NULL REFERENCES …` without a rebuild;
- leaves (nothing references them) can be rebuilt freely: `attribuzione`, `impronta_vocale`, and the three Sintesi
  tables as a group (children dropped first);
- since SQLite 3.26 (`legacy_alter_table=OFF`, the default) `ALTER TABLE … RENAME` rewrites the FK clauses of other
  tables that point at the renamed one.

## Decision

### 1. `7.sqm` (`persistenza/src/main/sqldelight/migrations/7.sqm`), in this order
```sql
-- Progetto (ADR 0033). Thin root: identity + Progetto; order, title, date are derived at read time.
CREATE TABLE incontro (
    id TEXT NOT NULL PRIMARY KEY,
    progetto_id TEXT NOT NULL REFERENCES progetto(id)
);
CREATE INDEX incontro_progetto ON incontro(progetto_id);
-- One Incontro per existing Registrazione; its id IS that Registrazione's id (a migration fact: no code relies on it).
INSERT INTO incontro(id, progetto_id) SELECT id, progetto_id FROM registrazione;

ALTER TABLE registrazione ADD COLUMN incontro_id TEXT REFERENCES incontro(id);
UPDATE registrazione SET incontro_id = id;
ALTER TABLE registrazione ADD COLUMN ora_di_inizio TEXT;      -- 'HH:MM:SS' local; NULL = empty (INV-I14); migrated rows empty
CREATE INDEX registrazione_incontro ON registrazione(incontro_id);
-- INV-I1 store backstops (the column cannot be NOT NULL without rebuilding registrazione): set at insert, never changed.
CREATE TRIGGER registrazione_incontro_obbligatorio BEFORE INSERT ON registrazione WHEN NEW.incontro_id IS NULL BEGIN SELECT RAISE(ABORT, 'registrazione.incontro_id obbligatorio'); END;
CREATE TRIGGER registrazione_incontro_immutabile BEFORE UPDATE OF incontro_id ON registrazione WHEN NEW.incontro_id IS NOT OLD.incontro_id BEGIN SELECT RAISE(ABORT, 'registrazione.incontro_id immutabile'); END;

-- Trascrizione (ADR 0035). Root "Voci dell'Incontro": one row from the first completion of any Parte until the
-- Incontro ceases. prossima_voce = INV-I4 (never reused in the Incontro).
CREATE TABLE voci_incontro (
    incontro_id TEXT NOT NULL PRIMARY KEY REFERENCES incontro(id),
    prossima_voce INTEGER NOT NULL
);
INSERT INTO voci_incontro(incontro_id, prossima_voce) SELECT registrazione_id, prossima_voce FROM trascritto;
-- A Voce of the Incontro (exists iff ≥ 1 Segmento in some Parte, INV-6). Target of the Parlanti FKs.
CREATE TABLE voce_incontro (
    incontro_id TEXT NOT NULL REFERENCES voci_incontro(incontro_id),
    numero INTEGER NOT NULL,
    PRIMARY KEY (incontro_id, numero)
);
INSERT INTO voce_incontro(incontro_id, numero) SELECT registrazione_id, numero FROM voce;

-- Parlanti: leaves rebuilt with the Incontro key.
CREATE TABLE attribuzione_v8 (
    incontro_id TEXT NOT NULL,
    voce_id INTEGER NOT NULL,
    progetto_id TEXT NOT NULL REFERENCES progetto(id),
    parlante_id TEXT NOT NULL REFERENCES parlante(id),
    PRIMARY KEY (incontro_id, voce_id),
    FOREIGN KEY (incontro_id, voce_id) REFERENCES voce_incontro(incontro_id, numero) DEFERRABLE INITIALLY DEFERRED
);
INSERT INTO attribuzione_v8(incontro_id, voce_id, progetto_id, parlante_id)
SELECT registrazione_id, voce_id, progetto_id, parlante_id FROM attribuzione;
DROP INDEX attribuzione_parlante;
DROP TABLE attribuzione;
ALTER TABLE attribuzione_v8 RENAME TO attribuzione;
CREATE INDEX attribuzione_parlante ON attribuzione(parlante_id);

CREATE TABLE impronta_vocale_v8 (
    parlante_id TEXT NOT NULL REFERENCES parlante(id),
    incontro_id TEXT NOT NULL,
    voce_id INTEGER NOT NULL,
    registrazione_id TEXT NOT NULL,                 -- the Parte the print is sourced from (INV-I8)
    impronta BLOB NOT NULL,
    sorgente_impronta TEXT NOT NULL,
    modello_impronta TEXT NOT NULL,
    FOREIGN KEY (incontro_id, voce_id) REFERENCES voce_incontro(incontro_id, numero) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (registrazione_id, voce_id) REFERENCES voce(registrazione_id, numero) DEFERRABLE INITIALLY DEFERRED,
    UNIQUE (parlante_id, incontro_id, voce_id, registrazione_id)
);
INSERT INTO impronta_vocale_v8(parlante_id, incontro_id, voce_id, registrazione_id, impronta, sorgente_impronta, modello_impronta)
SELECT parlante_id, registrazione_id, voce_id, registrazione_id, impronta, sorgente_impronta, modello_impronta FROM impronta_vocale;
DROP INDEX impronta_vocale_voce;
DROP TABLE impronta_vocale;
ALTER TABLE impronta_vocale_v8 RENAME TO impronta_vocale;
CREATE INDEX impronta_vocale_voce ON impronta_vocale(incontro_id, voce_id);
CREATE INDEX impronta_vocale_parte ON impronta_vocale(registrazione_id);

-- Sintesi (ADR 0037): the three tables rebuilt as a group, children dropped first.
CREATE TABLE riassunto_v8 (
    id TEXT NOT NULL PRIMARY KEY,
    incontro_id TEXT NOT NULL REFERENCES incontro(id),
    stato TEXT NOT NULL CHECK (stato IN ('in_attesa', 'in_corso', 'pronto', 'fallito')),
    argomento TEXT,
    lunghezza_massima_parole INTEGER NOT NULL CHECK (lunghezza_massima_parole > 0),
    richiesto_alle INTEGER NOT NULL,
    avviato_alle INTEGER,
    motivo_fallimento TEXT,
    sommario TEXT,
    omessi INTEGER,
    struttura TEXT,                                  -- StrutturaIncontro.chiave (ADR 0037 §5), NOT NULL iff pronto
    CHECK ((stato = 'fallito') = (motivo_fallimento IS NOT NULL)),
    CHECK ((stato = 'pronto') = (struttura IS NOT NULL AND omessi IS NOT NULL)),
    CHECK (stato = 'pronto' OR sommario IS NULL)
);
INSERT INTO riassunto_v8 SELECT id, registrazione_id, stato, argomento, lunghezza_massima_parole, richiesto_alle, avviato_alle,
    motivo_fallimento, sommario, omessi, CASE WHEN struttura IS NULL THEN NULL ELSE registrazione_id || '=' || struttura END
FROM riassunto;
CREATE TABLE riassunto_elemento_v8 ( …same columns and CHECKs as 6.sqm…, REFERENCES riassunto_v8(id) );
INSERT INTO riassunto_elemento_v8 SELECT * FROM riassunto_elemento;
CREATE TABLE riassunto_fonte_v8 (
    riassunto_id TEXT NOT NULL,
    tipo TEXT NOT NULL,
    posizione INTEGER NOT NULL,
    registrazione_id TEXT NOT NULL,                  -- the Parte of the Fonte; NO FK: survives a non-last Parte's deletion (D-0003)
    segmento_id INTEGER NOT NULL,
    PRIMARY KEY (riassunto_id, tipo, posizione, registrazione_id, segmento_id),
    FOREIGN KEY (riassunto_id, tipo, posizione) REFERENCES riassunto_elemento_v8(riassunto_id, tipo, posizione)
);
INSERT INTO riassunto_fonte_v8 SELECT f.riassunto_id, f.tipo, f.posizione, r.registrazione_id, f.segmento_id
FROM riassunto_fonte AS f INNER JOIN riassunto AS r ON r.id = f.riassunto_id;
DROP TABLE riassunto_fonte;
DROP TABLE riassunto_elemento;
DROP INDEX riassunto_registrazione;
DROP INDEX riassunto_non_pronto_unico;
DROP INDEX riassunto_pronto_unico;
DROP TABLE riassunto;
ALTER TABLE riassunto_v8 RENAME TO riassunto;
ALTER TABLE riassunto_elemento_v8 RENAME TO riassunto_elemento;
ALTER TABLE riassunto_fonte_v8 RENAME TO riassunto_fonte;
CREATE INDEX riassunto_incontro ON riassunto(incontro_id);
CREATE UNIQUE INDEX riassunto_non_pronto_unico ON riassunto(incontro_id) WHERE stato IN ('in_attesa', 'in_corso', 'fallito');
CREATE UNIQUE INDEX riassunto_pronto_unico ON riassunto(incontro_id) WHERE stato = 'pronto';
```
The block writes the elided `riassunto_elemento_v8` columns verbatim from `6.sqm`. Indexes of a dropped table are
dropped explicitly (the SQLDelight compiler does not, see `2.sqm`); each partial unique index stays on ONE line (ADR 0007).

### 2. What each table now means
- **`registrazione.incontro_id`**: nullable in SQL only because of SQLite; never NULL and never changed — the
  aggregate ([INV-I1], invariant test), the repository mapping (non-null type) and the two triggers. *If the
  SQLDelight 2.1 compiler rejects the triggers, the block says so in its PR and the triggers are dropped by an
  amendment of this ADR; nothing silently.*
- **`voce`** keeps its schema; its meaning narrows to "Voce n of the `Incontro` has ≥ 1 `Segmento` in this `Parte`"
  (a per-`Parte` presence row). `segmento`'s deferred FK to it is unchanged, so `segmento` is not rebuilt.
- **`voce_incontro`** is the `Voce` itself; `attribuzione` and `impronta_vocale` reference it (deferred). Both are
  written ONLY by the Voci dell'Incontro repository (ADR 0035 §1), with `voci_incontro`.
- **`impronta_vocale`** has TWO deferred FKs: to the `Voce` of the `Incontro` and to the `Voce`'s presence in the
  source `Parte`. A Parlanti purge that forgets a print of a removed `Voce`, or of a `Parte` slice that a `Revisione`
  or a re-transcription emptied, fails the COMMIT (fail closed, as ADR 0018/0020 rely on).
- **`trascritto.prossima_voce`** is retired: never read; new rows write `0`. `trascritto.prossimo_segmento` is now
  monotonic across replacements ([INV-I16]): a replacement updates the row, never re-inserts it from 1.
- **`riassunto.incontro_id`** keeps ADR 0022's **IMMEDIATE** FK, now to `incontro`: a composition that forgot Sintesi's
  subscriber fails the deletion of the **last** `Parte` (ADR 0038). For a non-last `Parte` nothing is deleted on purpose
  (D-0003) and `riassunto_fonte.registrazione_id` has no FK.
- **Set rules on the store (ADR 0007 pattern), re-keyed:** [INV-S2]/[INV-S3] per `Incontro` →
  `riassunto_non_pronto_unico`, `riassunto_pronto_unico` on `incontro_id`; "a `Voce` once per `Incontro`" →
  `voce_incontro`'s primary key; "one `Attribuzione` per `Voce`" → `attribuzione`'s primary key; "one print per
  (`Parlante`, `Voce`, `Parte`)" ([INV-I8]) → `impronta_vocale`'s UNIQUE.

### 3. Privacy of the copied prints (ADR 0009)
The migration copies every print into `impronta_vocale_v8` and drops the old table: `secure_delete=ON` zeroes the
freed pages. The open path runs `checkpointDopoCommit()` (`PRAGMA wal_checkpoint(TRUNCATE)`) after a migration that
crossed version 8, so the old pages do not linger in the WAL (the same rule as every print-removal path, ADR 0020 §3).

### 4. Migration test (`:persistenza:test`, extended; ADR 0006 (a))
A **pre-feature database** (schema 7, built by `1.sqm`…`6.sqm` and seeded through the old queries: a `Registrazione`
with a revised `Trascritto` whose `prossima_voce` exceeds its highest `Voce`, `Attribuzione`s, prints, a `pronto` and a
`fallito` `Riassunto` with `Fonte`s) migrated to 8, then:
- `integrity_check`, `foreign_key_check` clean; `PRAGMA foreign_key_list(riassunto_elemento)` targets `riassunto`, and
  `riassunto_fonte`'s targets `riassunto_elemento` (the rename rewrite happened);
- one `incontro` per `registrazione`, `incontro_id = id`, `ora_di_inizio` NULL;
- `voci_incontro.prossima_voce` = the old `prossima_voce`; same `Voce` numbers, same `Attribuzione`s, every print with
  `registrazione_id` = its `Registrazione` ([INV-I3]);
- every `riassunto` row and child row is there with the same values, `Fonte`s carry `registrazione_id`;
- **the migrated `pronto` `Riassunto` is NOT `superato`**: `StrutturaIncontro` of the unchanged `Trascritto` equals the
  re-encoded `struttura` (ADR 0037 §5) — the pure predicate is run on the migrated rows;
- a NULL `incontro_id` insert and an `incontro_id` update are refused by the triggers;
- `Schema.migrate` from empty equals `Schema.create`, every new query runs once.

### 5. Rule 9: persistent writes only in the aggregate's adapter *(amended 2026-10-01, build-manifest checkpoint [user])*
The prohibition check above (clause 1) is the "writes only in its adapter" constraint of the four aggregates of the
feature: `incontro` (ADR 0033 §7), `voci-dell-incontro` and `parlante-impronte-per-parte` (ADR 0035 §9),
`riassunto-incontro` (ADR 0037 §9). It holds on today's tree (every use of those query families is already in the
owner's persistence package), so its owner stays `persistenza-incontro` (wave 1). The other two rule-9 constraints
(mutation only through the root, invariant fields read only inside) are the owning ADRs' checks.

## Rejected options
- **Rebuilding `registrazione` to make `incontro_id NOT NULL`**: the three immediate FKs that reference it make a rebuild
  impossible inside the migration transaction with `foreign_keys=ON`.
- **A random id per migrated `Incontro`** (`randomblob`): non-deterministic migration and test, and the sweep of ADR 0033
  §6 would stop being behaviour-neutral. The equal-id fact is used by the migration only.
- **Re-keying `voce` to `(incontro_id, numero)`**: needs a rebuild of `segmento` (FK mismatch) for nothing — the
  per-`Parte` presence row is exactly what the print's second FK needs.
- **Dropping the Parlanti → `voce` FKs**: loses the fail-closed purge guarantee of ADR 0018 / ADR 0020.
- **An FK from `riassunto_fonte.registrazione_id` to `registrazione`**: it would veto the deletion of a non-last `Parte`,
  which the user decided keeps the `Riassunto` (D-0003).
- **A digest instead of the canonical `struttura` text**: the precedent of ADR 0022 stands (exact, collision-free, one
  equality); size grows linearly with the `Incontro` (about 8 KB per hour).

## Consequences
- `Schema.version` becomes 8. ADR 0022's two checks stay green (they read `6.sqm`, which is never edited) but no longer
  describe the live schema: this ADR's checks do.
- Every repository touching a rebuilt or re-keyed table changes in the sweep of ADR 0033 §6 (block
  `persistenza-incontro`, proposed; pinned by build-manifest).
- Enforcement: the two checks above (presence + prohibition) from `persistenza-incontro`; red by design before it (the
  script does not exist yet — see the feature's architecture overview, "Gate note"). Discursive (code review): no
  shipped `.sqm` edited (CR-13); child rows deleted explicitly, never by cascade; the struttura re-encoding in SQL equals
  `StrutturaIncontro.chiave` for one `Parte` (the migration test proves it).
