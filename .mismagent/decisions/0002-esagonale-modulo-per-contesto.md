---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by:   # migrated 2026-09-26 (mismAgent 0.22) from the legacy inline shell rule: same grep/find logic, now versioned checks run by the gate (architettura-test ControlliAdrTest, red-green on fixture/<check>/)
  - check: architettura-test/controlli-adr/adr-0002-nucleo-senza-tecnologia.sh
---
# 0002 — Style: hexagonal (ports & adapters), one Gradle module set per bounded context

## Context
Quality drivers collected in pass-1: (1) ML adapters swappable behind stable seams (4 open ML
spikes, models will change); (2) correctness of the biometric purge; (3) integrity of the source
of truth across long-running work (INV-3/4/5); (4) headless testability by an agent (no weights,
no network, no display in the gate); (5) longevity — a v2 `Sintesi` consumer must plug in
downstream without touching `Trascrizione`; (6) offline after setup. Alternatives: hexagonal
module-per-context vs plain layered per context. **Hexagonal chosen [user].**

## Decision
- Module map, allowed dependency directions and package names: **`.mismagent/architecture.md`**
  (the single source; this ADR does not repeat it).
- **Dependency rule:** `adattatori → applicazione → dominio → :kernel`, never outward. A context
  never depends on another context's `dominio` or `adattatori`; a cross-context read goes through a
  **consumer-owned port** declared in the consumer's `applicazione`, implemented by an adapter in the
  consumer's `adattatori` that calls the supplier's `applicazione` public query API.
- The inner modules (`:kernel`, `*:dominio`, `*:applicazione`) contain **no** framework, I/O,
  persistence, UI, ML, audio or network code — including JDK packages the Gradle graph cannot
  block (`java.sql`, `java.net`, `java.nio.file`, `java.io.File`, `javax.sound`). File/audio
  references cross the inner layers as opaque value types (e.g. a `RiferimentoAudio` string VO).
- **Enforcement (three channels, all in `./gradlew check`):**
  1. the **Gradle module graph** (an undeclared module dependency does not compile) plus a root
     task `verificaDipendenzeModuli` that fails on any project-dependency edge not in
     `architecture.md`'s allowed-edges table (catches a worker adding an edge in a build file);
  2. **Konsist** tests in `:architettura-test` (import/package rules, see `code-rules.md`);
  3. this ADR's `enforced_by` (the verifier's grep backup for the inner modules).
- Build files reference projects with `project(":…")` notation only (no type-safe accessors), so
  the edge check stays greppable.

## Consequences
- All in-process boundaries are consumer-owned ports + in-process contract tests (single side).
- Published Language at in-process seams = the shared-kernel VOs (`VoceRef`, `IntervalloMs`, ids),
  not stringly primitives.
- More modules (≈20) and indirection; accepted for swappability and compiler-enforced boundaries.
