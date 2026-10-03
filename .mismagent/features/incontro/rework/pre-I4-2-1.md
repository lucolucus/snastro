# pre-I4-2 — trascrizione: open I4 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination. Touch only the files the lines name (plus their tests); other groups are building in parallel on the modules listed under 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): voci-del-trascritto-incontro, adattatori-trascrizione-incontro, pre-I3-5

## Notes
- You MAY add a test-only driver factory to :persistenza testFixtures for L241 (keep the production DriverSqliteImmediato settings; do not change the production driver).

## Lines (pre-release.md line number: text)
- L241: I4 · pre-I3-5 · LOW · trascrizione/adattatori/src/test/kotlin/snastro/trascrizione/adattatori/persistenza/VociDelTrascrittoLetturaCoerenteSqlTest.kt:123-130 · AC-I42 now runs on a hand-built JdbcSqliteDriver (WAL+FK only), not the production DriverSqliteImmediato (IMMEDIATE writes, DEFERRED reads, busy_timeout, secure_delete); a test-only factory from :persistenza testFixtures would keep the production driver · verifier, code-review (opus) · 2026-10-03
