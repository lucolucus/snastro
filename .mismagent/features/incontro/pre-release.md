# Pre-release — incontro

MED/LOW findings, one line each (worker-composer). Format: release · id · sev · file:line · issue · reviewer · date.

- [ ] I1 · gate-controlli-differiti · MED · architettura-test/src/test/kotlin/snastro/architettura/RegistroControlliAdr.kt:24 · a `from` naming no block (typo, renamed or deleted block) is deferred forever (fail-open); defer only when `from` is a block id of some building-blocks.yaml or a blocks/*/*/<from>.md, else fail · reviewer (general-purpose, opus) · 2026-10-01
- [ ] I1 · gate-controlli-differiti · MED · architettura-test/src/test/kotlin/snastro/architettura/RegistroControlliAdr.kt:84 · quoted YAML values kept raw: a quoted existing `check:` path with an unintegrated `from` is deferred, never tested; strip quotes, defer only controlli-adr/*.sh paths · reviewer (general-purpose, opus) · 2026-10-01
- [ ] I1 · gate-controlli-differiti · LOW · architettura-test/src/test/kotlin/snastro/architettura/RegistroControlliAdr.kt:59 · inline flow list on the `enforced_by:` line itself is silently dropped (pre-existing gap) · reviewer (general-purpose, opus) · 2026-10-01
- [ ] I1 · gate-controlli-differiti · LOW · architettura-test/src/test/kotlin/snastro/architettura/RegistroControlliAdrTest.kt · no unit test for the done/<from>.md and BLOCCHI_COSTRUITI_SENZA_MARCATORE paths; the comment-stripping case does not discriminate · reviewer (general-purpose, opus) · 2026-10-01
- [ ] I1 · gate-controlli-differiti · LOW · architettura-test/src/test/kotlin/snastro/architettura/RegistroControlliAdr.kt:25 · a check path that is a directory counts as missing and is deferred · reviewer (general-purpose, opus) · 2026-10-01
