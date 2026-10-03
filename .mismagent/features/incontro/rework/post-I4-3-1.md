# post-I4-3 — kernel: open post-I4 lines

Fix every line below in this group's files, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination (a test you add must fail on the old code: say how you saw it red). Touch only the files the lines name (plus their tests); other groups build in parallel — respect 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md; fix groups: .mismagent/features/incontro/rework/<id>-*.md): pre-I4-5, avvio-incontro-parti (kernel :kernel, DispatcherEventiInMemoria)

## Notes
- D-0065/D-0066 stand. L272: decide (DECISIONS) whether a failing priority subscriber should stop the ordinary deliveries of that commit (stale data never served) or keep today's behaviour with a documented reason; test whichever you choose in DispatcherEventiInMemoriaTest and, if the port contract is affected, in DispatcherEventiContratto. Touch only kernel/ (and avvio wiring only if strictly needed).

## Lines (pre-release.md line number: text)
- L271: post-I4 · pre-I4-5 · LOW · kernel/src/main/kotlin/snastro/kernel/DispatcherEventiInMemoria.kt:122-139 · priority-first per commit breaks if a priority subscriber opens a transaction (nested full delivery runs before the other priority subscribers see the outer events); unreachable today (only priority subscriber opens none) — say so in the KDoc · code-review (opus) · 2026-10-03
- L272: post-I4 · pre-I4-5 · LOW · kernel/src/main/kotlin/snastro/kernel/DispatcherEventiInMemoria.kt:126-133,145 · a priority (invalidation) subscriber throwing an ordinary exception lets the ordinary reloads run on the stale Proposta cache, shown as Ok+WARNING; an Error there skips the invalidation for the rest of the commit · code-review (opus), verifier (opus) · 2026-10-03
