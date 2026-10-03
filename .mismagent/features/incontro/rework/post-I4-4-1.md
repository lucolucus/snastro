# post-I4-4 — avvio (test): open post-I4 lines

Fix every line below in this group's files, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination (a test you add must fail on the old code: say how you saw it red). Touch only the files the lines name (plus their tests); other groups build in parallel — respect 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md; fix groups: .mismagent/features/incontro/rework/<id>-*.md): avvio-proposta-tra-parti, pre-I4-4

## Notes
- Only PropostaTraPartiComposizioneTest.kt. Prove the new bound turns the old hang into a failure: temporarily revert PropostaTraPartiProgetto's cache-before-lock read in your worktree, see red within the bound, restore.

## Lines (pre-release.md line number: text)
- L278: post-I4 · pre-I4-4 · MED · avvio/src/test/kotlin/snastro/avvio/parlanti/PropostaTraPartiComposizioneTest.kt:~176 · the L249 test reads traParti(primo) on the test thread while the Mutex is released only in finally: a regression hangs the gate forever (no JUnit timeout configured) instead of failing; run the read in async + withTimeout or assertTimeoutPreemptively · verifier (sonnet) · 2026-10-03
