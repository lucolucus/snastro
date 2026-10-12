# Smoke renders

**Rule.** A slice that must keep the smoke renders unchanged compares them between two `git archive` copies, Base and
HEAD, extracted in the scratchpad and each rendered with the `smoke:` command of `CLAUDE.md`
(`./gradlew :avvio:test --tests 'snastro.avvio.smoke.SmokeTest'`), then compared with `cmp` or `shasum`. Never compare
against the main checkout's `avvio/build/smoke/`: it keeps stale files no test writes any more (e.g. `s3.png`).

**Why.** Slices 36, 38, 39 and 40 each rebuilt this procedure, and in slice 40 the main checkout disagreed with Base's
archive while two archives agreed. Written once, the comparison is the same for every slice. Decided: the user,
2026-10-12.

**Shown by:** `avvio/src/test/kotlin/snastro/avvio/smoke/SmokeTest.kt`, `avvio/src/main/kotlin/snastro/avvio/smoke/Smoke.kt`.
