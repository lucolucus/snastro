# pre-I3-7 — kernel + avvio: L237 (2), decided FIX (Codex, delegated by the user)

Fix the line below with tests. The other half of L237 (dedup per commit) is WAIVED — do not touch it.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): avvio-incontro-parti, avvio-proposta-tra-parti (composition root); kernel module :kernel (snastro.kernel, DispatcherEventiInMemoria).

## Decision to implement
- After a successful COMMIT, a failure of an after-commit subscriber (DispatcherEventiInMemoria.consegnaDopoCommit, rethrown by the wrapping unit of work `esterna()`) must be distinguishable from a commit failure: introduce a distinct, additive kernel exception type (e.g. `ConsegnaDopoCommitFallita`, carrying the first cause, the others suppressed) thrown only for post-commit delivery failures. Commit failures keep their current type.
- AzioniSomiglianzaProgetto.applica (avvio/src/main/kotlin/snastro/avvio/parlanti/AzioniSomiglianzaProgetto.kt:~162-167): on that type, show the COMMITTED outcome (the plan was applied) plus a refresh warning, never the generic "Errore". Check the other call sites that catch Exception around inTransazione in :avvio and apply the same rule where the user would otherwise be told a committed change failed (list them under DECISIONS).
- Tests: kernel — a throwing after-commit subscriber yields the new type and the transaction is committed; a commit failure still yields the old type. avvio — applica with a throwing subscriber reports the committed outcome.
- Additive only: no existing kernel signature changes. Say under DEVIATIONS exactly what is new in the kernel's public surface.

## Lines (pre-release.md line number: text)
- L237 (part 2): I3 · pre-I2-6 · LOW · avvio/src/main/kotlin/snastro/avvio/parlanti/AzioniSomiglianzaProgetto.kt:162-167 · a throwing after-commit subscriber shows a generic Errore though the plan committed · verifier · 2026-10-03
