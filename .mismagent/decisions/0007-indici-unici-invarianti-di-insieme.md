---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by:
  kind: presence
  rule: "grep -rhE --include='*.sq' --include='*.sqm' '^CREATE UNIQUE INDEX .*\\(registrazione_id\\) WHERE .*in_attesa.*in_corso' persistenza | grep -q . && grep -rhE --include='*.sq' --include='*.sqm' '^CREATE UNIQUE INDEX .*\\(registrazione_id\\) WHERE .*completata' persistenza | grep -q . && grep -rhE --include='*.sq' --include='*.sqm' '^CREATE UNIQUE INDEX .*\\(progetto_id, nome_normalizzato\\) WHERE .*attivo' persistenza | grep -q ."
  exigible_from: "persistenza-schema"
---
# 0007 — Set invariants INV-4 and INV-16 backed by partial unique indexes

## Context
The tactical model leaves two **set rules across aggregate instances** to "repository uniqueness
(architect)": [INV-4] per `Registrazione`, at most one `Elaborazione` `in_attesa|in_corso` and at
most one `completata`; [INV-16] `Nome` unique (trimmed, case-insensitive) among the `attivo`
`Parlante`s of a `Progetto`, `eliminato` names reusable. An application-service check alone is racy
and can be bypassed by a future code path; the store must refuse the violating row.

## Decision
- **INV-4** (table `elaborazione`), two partial unique indexes, **each written on ONE line** (keeps
  the guard greppable):
  - `CREATE UNIQUE INDEX elaborazione_aperta_unica ON elaborazione(registrazione_id) WHERE stato IN ('in_attesa', 'in_corso');`
  - `CREATE UNIQUE INDEX elaborazione_completata_unica ON elaborazione(registrazione_id) WHERE stato = 'completata';`
  "A new `Elaborazione` only if every previous one is `fallita`" = both indexes together (a
  `completata` row blocks the open index's precondition in the application service, and the second
  index blocks a second `completata`).
- **INV-16** (table `parlante`): a stored column **`nome_normalizzato`** = `nome.trim()` lowercased
  with `Locale.ROOT` (computed in the `Parlante` aggregate's `Nome` VO, never in SQL), and
  - `CREATE UNIQUE INDEX parlante_nome_attivo_unico ON parlante(progetto_id, nome_normalizzato) WHERE stato = 'attivo';`
- The application services (avvia-elaborazione; crea/rinomina/promuovi/conferma-attribuzione/
  salta-voce) **check first and return `Esito.Errore(...)`** (ADR 0003); the index is the backstop —
  a constraint violation surfacing from the store is mapped by the repository adapter to the same
  `ErroreDominio` (`ElaborazioneGiaAperta` / `NomeGiaInUso`), never a raw exception to the UI.
- The provisional "Ospite del <data>" name ([INV-19]) gets its numeric suffix from the same
  normalized comparison.
- Keys: `VoceRef = (registrazioneId, voceId)` is the key of `Attribuzione` and `ImprontaVocale` →
  `PRIMARY KEY (registrazione_id, voce_id)` on `attribuzione`, `UNIQUE (parlante_id, registrazione_id,
  voce_id)` on `impronta_vocale` ([INV-14]); `voceId` never reused ([INV-12]) is guaranteed by the
  `Trascritto` aggregate (monotonic counter persisted with it).

## Consequences
- Presence rule, wave-gated on the two persistence adapter blocks; the invariant tests of the
  application services remain the primary proof (tests against the real SQLite in the adapters'
  round-trip tests include a concurrent-insert case).

## Amendment 2026-09-23 (build-manifest reconciliation R19)
`exigible_from` pinned to block **`persistenza-schema`** (feature trascrizione-con-parlanti): the index DDL
lives in the `:persistenza` `.sq` owned by that block, so the presence rule is exigible once it merges.
The round-trip + concurrent-insert proof stays on `repository-sql-trascrizione` and
`repository-sql-parlanti` (their ACs), and the constraint → `ErroreDominio` mapping also covers
`ElaborazioneGiaCompletata` for the second index.
