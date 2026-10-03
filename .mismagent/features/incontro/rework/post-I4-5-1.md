# post-I4-5 — architettura-test + sintesi: open post-I4 lines

Fix every line below in this group's files, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination (a test you add must fail on the old code: say how you saw it red). Touch only the files the lines name (plus their tests); other groups build in parallel — respect 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md; fix groups: .mismagent/features/incontro/rework/<id>-*.md): riassunto-vista-incontro, pre-I4-6

## Notes
- Each script change gets a violante (old passes, new fails) or conforme fixture; every awk loop must provably shrink. Run both scripts on the whole project tree: no new false positive. Changes under architettura-test/** stale the gate proof; the composer re-records it.

## Lines (pre-release.md line number: text)
- L279: post-I4 · pre-I4-6 · LOW · architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh:149-155 · a column wrapped in a function as a comparison operand or BETWEEN bound is not flagged (`coalesce(ora_di_inizio, 0) > 1`, `x BETWEEN 1 AND coalesce(ora_di_inizio,0)` pass); none in today's SQL · verifier (opus), code-review (opus) · 2026-10-03
- L280: post-I4 · pre-I4-6 · LOW · architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh:120-131,128,158 · ORDER BY on a column renamed in a FROM subquery not flagged; implicit-alias regex can false-positive on a select item ending in a bare column; `x->>ora_di_inizio` flagged by the [<>]=? rule — contrived, absent from today's code · code-review (opus) · 2026-10-03
- L281: post-I4 · pre-I4-6 · LOW · architettura-test/src/test/kotlin/snastro/architettura/ControlliAdrTest.kt:166-170 · the timeout/kill path has no test of its own; nothing waits on the process after destroyForcibly · code-review (opus) · 2026-10-03
- L282: post-I4 · pre-I4-6 · LOW · sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/porte/RiassumibilitaInDuePassi.kt:28-38, sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/porte/RiassumibilitaInDuePassiTest.kt:53 · KDoc blames "a concurrent run" but the only Trascritto-deleting path is eliminaParte (then "Parte N in trascrizione" is inaccurate, transient); the renamed L229 test only proves pass-through · code-review (opus), verifier (opus) · 2026-10-03
