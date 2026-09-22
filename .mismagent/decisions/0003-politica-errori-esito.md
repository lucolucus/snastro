---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' 'sealed[[:space:]]+(class|interface)[[:space:]]+ErroreDominio.*(Exception|Throwable|Error)[[:space:]]*\\(' kernel | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
---
# 0003 — Error policy: expected failures are `Esito` values, exceptions only for bugs/infra

## Context
Knob K3 (pass-1). The Python-phrased recommendation was a typed exception hierarchy; translated to
Kotlin, the idiomatic split (expected business failure = value; bug/infra fault = exception)
changes its meaning, so it was re-confirmed: **`Esito` sealed [user]**.

## Decision
- `:kernel` declares `sealed interface Esito<out T>` with `Ok<T>(valore)` and `Errore(errore)` and
  `sealed interface ErroreDominio` (**not** a `Throwable`). Each context declares its own sealed
  subtypes (e.g. `NomeGiaInUso`, `TransizioneNonAmmessa`, `VoceNonTrovata`,
  `DivisioneNonAmmessa`). No Arrow / third-party result library.
- Aggregate methods and application services return `Esito` for every **expected** rule violation
  (every `[INV-n]` a user action can trip). They never throw for those.
- Exceptions are reserved for **programmer errors** (`require`/`check` on impossible states) and
  **infrastructure faults** (I/O, SQLite, native ML). An adapter translates infra faults at the
  boundary: in `AvviaElaborazione` any pipeline fault ends as `ElaborazioneFallita(motivo)` with a
  plain-language reason (the user sees it in S2 with "Riprova").
- Presenters map `ErroreDominio` to inline messages with an **exhaustive `when`** (no `else`
  branch on sealed error types — a new error type must break compilation where it is not shown).
- No swallowed failures: no empty `catch`, no `runCatching { }` whose failure is dropped
  (detekt `SwallowedException`, `EmptyCatchBlock`, `TooGenericExceptionCaught` in the gate).

## Consequences
- `enforced_by` guards that `ErroreDominio` never becomes an exception type; the rest is gate lint
  (detekt) + code-review criteria (`code-rules.md`).
- Tests assert on `Esito.Errore(<specific type>)`, not on thrown exceptions, for invariants.
