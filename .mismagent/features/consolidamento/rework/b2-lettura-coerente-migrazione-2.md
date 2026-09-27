# Rework b2-lettura-coerente-migrazione — cycle 2 of 2, LAST (verifier FAIL + code-review CHANGES on be57dcb)
Re-read BOTH findings files (this one and b2-lettura-coerente-migrazione-1.md) before touching code.
AC-C28, AC-C35 and the first half of AC-C29 now PASS (probes confirmed). One HIGH remains:

## HIGH — AC-C29 doom half does not test the doom
`TrascrittoRepositorySqlTrovaInTransazioneTest` › "una trova che lancia dentro inTransazione condanna l'intera unita…"
lets the injected exception ESCAPE the outer `inTransazione`, so SQLDelight rolls back on the exception alone.
Probe: removing the doom in `UnitaDiLavoroSql.inLettura` (Modo.SCRITTURA branch,
`runCatching(blocco).onFailure { condanna(stato, it) }.getOrThrow()` → `blocco()`) leaves BOTH AC-C29 tests green.
ADR 0029 §2 rule 6 / contract case 7: the read dooms the whole transaction "even if the outer block catches it and
returns Ok".
Fix (test only): after `salva`, CATCH the throwing trova inside the block (`runCatching { repo.trova(R) }`) and return
`Esito.Ok(Unit)`; assert `inTransazione` still ends without committing (the IllegalStateException "transazione
annidata e fallita" whose cause is the injected fault) and that a fresh `repo.trova(R)` is null. Then run the
doom-removal probe on a throwaway copy and show the test goes RED. Fix the test name/KDoc to say what it proves.
