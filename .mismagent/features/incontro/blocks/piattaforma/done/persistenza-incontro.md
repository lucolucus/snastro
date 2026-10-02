---
id: persistenza-incontro
type: adapter
context: piattaforma
side: app
wave: 1
release: I1
high_value: true
module: ":persistenza (migrations/7.sqm, *.sq) + the SQL repositories of :progetto:adattatori, :trascrizione:adattatori, :parlanti:adattatori, :sintesi:adattatori (query renames only) + architettura-test/controlli-adr"
consumes: []
related_adrs:
  - "0006"
  - "0007"
  - "0009"
  - "0022"
  - "0034"
tests_nl_status: confirmed
---
# persistenza-incontro

## What to do
Write 7.sqm exactly as ADR 0034 §1 (incontro, registrazione.incontro_id + ora_di_inizio + two triggers, voci_incontro, voce_incontro, Parlanti and Sintesi leaves rebuilt, struttura re-encoded) and the matching .sq queries; extend the migration test (ADR 0034 §4); keep every existing repository compiling and behaving the same under the current domain types by resolving incontro_id from registrazione_id in SQL; write ADR 0034's two check scripts with fixtures.

## Tasks
- AC-I1 migration test: a pre-feature DB (schema 7: a Registrazione with a revised Trascritto whose prossima_voce exceeds its highest Voce, two Attribuzioni, prints, a pronto and a fallito Riassunto with Fonti) migrates to 8 with integrity_check and foreign_key_check clean
- AC-I2 after migration: exactly one incontro row per registrazione, registrazione.incontro_id set on every row, ora_di_inizio NULL on every row; voci_incontro.prossima_voce equals the old trascritto.prossima_voce; same Voce numbers, same Attribuzioni, every print with registrazione_id = its Registrazione
- INV-I3 every riassunto, elemento and fonte row is preserved, each Fonte carries its registrazione_id, and the struttura is re-encoded as '<registrazioneId>=' + old encoding; the NOT-superato proof (domain predicate on the migrated rows) lives in :sintesi:adattatori RiassuntoMigratoDaV7Test (ADR 0034 Amendment 2026-10-03)
- AC-I3 the triggers refuse an INSERT into registrazione with NULL incontro_id and an UPDATE that changes incontro_id (RAISE ABORT), and accept an UPDATE of other columns
- AC-I4 the rebuilt indexes: a second open Riassunto (in_attesa) for the same incontro_id violates riassunto_non_pronto_unico; a second pronto violates riassunto_pronto_unico; a second impronta for the same (parlante, incontro, voce, registrazione) violates the UNIQUE
- AC-I5 Schema.migrate from empty equals Schema.create; every new or changed query runs once against the migrated DB; PRAGMA foreign_key_list(riassunto_fonte) targets riassunto_elemento (rename rewrite happened)
- AC-I6 behaviour-neutral before incontro-chiavi: the existing repository contract tests (Trascritto, Attribuzione, Parlante, Riassunto, Registrazione) pass unchanged against the migrated schema; a new Registrazione saved by the repository gets its own new incontro row (one Parte) in the same transaction
- AC-I7 after a migration that crossed version 8 the open path runs the WAL checkpoint (TRUNCATE): the old impronta_vocale pages are not left in the -wal file (test on a real file DB)
- AC-I8 adr-0034-schema-incontro.sh exits 0 on the tree and 1 on each violating fixture of ADR 0034 (each clause removed, an index on registrazione_id, the riassunto FK DEFERRABLE, 7.sqm absent); adr-0034-tabelle-incontro-confinate.sh exits 0 on the tree and 1 on each violating fixture, 0 on each conforming one

## Notes
Owner of ADR 0034's checks (from: persistenza-incontro). The SQL joins that keep today's repositories working are a transition removed by incontro-chiavi (header § TRANSITION). If SQLDelight 2.1 rejects the triggers, say so in the PR: ADR 0034 §2 is amended, never silently.

Sources: ADR 0034 §1-§4 · ADR 0033 §6 · tactical-model.md § Migration · architecture-overview.md A-5, A-6, Gate note
