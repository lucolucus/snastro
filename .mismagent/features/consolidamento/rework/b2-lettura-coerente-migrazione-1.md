# Rework b2-lettura-coerente-migrazione — cycle 1 (verifier FAIL + code-review CHANGES on a81f625)
The migration itself is correct (probed); the FAIL is three CONFIRMED ACs whose required tests were not written.

## HIGH 1 — AC-C28 has no test
Add a repository-level test: while another thread holds an UNCOMMITTED `BEGIN IMMEDIATE` on the same file database,
`TrascrittoRepositorySql.trova` returns the last COMMITTED Trascritto within 1 s with no SQLITE_BUSY. The writer must
take the lock BEFORE the read starts (latch-driven, no sleep) — the existing AC-C31 case starts the reader first, which
the old IMMEDIATE read would also pass. Prove it discriminates (it must fail if trova reverts to an IMMEDIATE read).

## HIGH 2 — AC-C29 has no test
On SQL: `TrascrittoRepositorySql.trova` called inside `inTransazione` (the SAME UnitaDiLavoroSql) sees that unit's
uncommitted writes; and a throwing trova there dooms the unit end to end (nothing committed).

## HIGH 3 — AC-C35 identity not asserted
Assert in the composition test Ambiente (AmbienteR2 and AmbienteR3, or ComposizioneR*Test) that `contesto.lettura` is
the instance the dispatcher delegates to. Cheap behavioural proof suggested by the review:
`contesto.dispatcher.unitaDiLavoro.inTransazione { Esito.Ok(contesto.lettura.inLettura { 1 }) }.atteso()` — with a
second instance `noEnclosing` throws. Show it fails on a deliberately mis-wired copy (throwaway probe).
