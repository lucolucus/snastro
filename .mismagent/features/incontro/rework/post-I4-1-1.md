# post-I4-1 — progetto (tests): open post-I4 lines

Fix every line below in this group's files, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination (a test you add must fail on the old code: say how you saw it red). Touch only the files the lines name (plus their tests); other groups build in parallel — respect 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md; fix groups: .mismagent/features/incontro/rework/<id>-*.md): sonda-ora-di-inizio, aggiungi-registrazione-incontro, pre-I4-1

## Notes
- Keep the L242 race test's discrimination (red under BEGIN DEFERRED); prefer a deterministic hand-off (the second import provably blocked at BEGIN) over a timed window if you can.

## Lines (pre-release.md line number: text)
- L263: post-I4 · pre-I4-1 · LOW · progetto/adattatori/src/test/kotlin/snastro/progetto/adattatori/persistenza/ImportConcorrenteSqlTest.kt:57-60 · under BEGIN IMMEDIATE every gate run pays the full 500 ms latch bound; nothing asserts the second import blocked at BEGIN (ordering assertion only) · code-review (opus) · 2026-10-03
- L264: post-I4 · pre-I4-1 · LOW · progetto/adattatori/src/test/kotlin/snastro/progetto/adattatori/audio/InfoAudioDaSondaTest.kt:16,23 · tests tagged AC-I52/AC-I53, which in sonda-ora-di-inizio are the udta/date parsing rule, not the InfoFile→InfoAudio mapping; neutral name or L76 reference · verifier (opus) · 2026-10-03
- L265: post-I4 · pre-I4-1 · LOW · progetto/adattatori/src/test/kotlin/snastro/progetto/adattatori/persistenza/ImportConcorrenteSqlTest.kt:86-87 · a hung thread yields null from esito.get() → NPE in forEach instead of a clear timeout message · verifier (opus) · 2026-10-03
- L266: post-I4 · pre-I4-1 · LOW · ImportConcorrenteSqlTest · discrimination margin: under DEFERRED the mutant is red only if both reads overlap in the 500 ms window (5/5 red, 8/8 green measured); a heavily loaded host could let the mutant pass · verifier (opus) · 2026-10-03
