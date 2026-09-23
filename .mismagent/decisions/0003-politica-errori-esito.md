---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' --exclude-dir=build '(class|interface|object)[[:space:]][^:]*:.*(ErroreDominio|Errore[A-Z][A-Za-z]*).*(Exception|Throwable|Error)[[:space:]]*\\(|(class|interface|object)[[:space:]][^:]*:.*(Exception|Throwable|Error)[[:space:]]*\\(.*(ErroreDominio|Errore[A-Z][A-Za-z]*)|(class|interface)[[:space:]]+ErroreDominio[^{]*(Exception|Throwable|Error)[[:space:]]*\\(' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
amended: 2026-09-23   # R25 — see "Amendment 2026-09-23" (ErroreDominio non-sealed; enforced_by replaced) + "Amendment 2026-09-23 (b)" (error placement)
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
  *(Amended 2026-09-23: `ErroreDominio` is a plain interface; the sealed hierarchies are per
  context — see "Amendment 2026-09-23" below.)*
- Aggregate methods and application services return `Esito` for every **expected** rule violation
  (every `[INV-n]` a user action can trip). They never throw for those.
- Exceptions are reserved for **programmer errors** (`require`/`check` on impossible states) and
  **infrastructure faults** (I/O, SQLite, native ML). An adapter translates infra faults at the
  boundary: in `AvviaElaborazione` any pipeline fault ends as `ElaborazioneFallita(motivo)` with a
  plain-language reason (the user sees it in S2 with "Riprova").
- Presenters map `ErroreDominio` to inline messages with an **exhaustive `when`** (no `else`
  branch on sealed error types — a new error type must break compilation where it is not shown).
  *(Amended 2026-09-23: one exhaustive `when` per context hierarchy — see below.)*
- No swallowed failures: no empty `catch`, no `runCatching { }` whose failure is dropped
  (detekt `SwallowedException`, `EmptyCatchBlock`, `TooGenericExceptionCaught` in the gate).

## Consequences
- `enforced_by` guards that `ErroreDominio` never becomes an exception type; the rest is gate lint
  (detekt) + code-review criteria (`code-rules.md`).
- Tests assert on `Esito.Errore(<specific type>)`, not on thrown exceptions, for invariants.

## Amendment 2026-09-23 — R25: `ErroreDominio` is a plain interface; sealed hierarchies per context
**Why.** The build-manifest found (R25) that the original form cannot compile: Kotlin requires the
direct subtypes of a sealed type to live in the **same module and package**, but `ErroreDominio`
is in `:kernel` and its subtypes are declared in each context's modules. Deliberated with the user
on 2026-09-23 **[user]**.

**Amended decision.**
- `:kernel` declares **`public interface ErroreDominio`** — a plain, **non-sealed** marker interface,
  still **never a `Throwable`**. `Esito.Errore(errore: ErroreDominio)` is unchanged.
- Each context (and each technical module that returns `Esito`, e.g. `:modelli`) owns ONE
  **sealed** hierarchy, type **`Errore<Contesto>`**, declared in the file **`Errori<Contesto>.kt`**
  of the module that raises it (its `dominio`, or `applicazione` for application-only errors), e.g.
  `sealed interface ErroreParlanti : ErroreDominio { data class NomeGiaInUso(val nome: String) : ErroreParlanti; … }`.
  Every direct subtype of `ErroreDominio` is such a sealed interface (Konsist, CR-8).
  *(Placement clarified by "Amendment 2026-09-23 (b)" below.)*
- **Exhaustiveness per context:** `MessaggiErrore.kt` (`:ui`) has one `messaggioPer(e: Errore<Contesto>)`
  per hierarchy with an exhaustive `when` and **no `else`**; the entry point
  `messaggioPer(e: ErroreDominio)` dispatches on the per-context types and its only `else` is a
  programmer error (`error("ErroreDominio non mappato: …")`), made unreachable by CR-8's Konsist rule
  plus a `:ui` test that maps one instance of every context hierarchy.
- **Meaning preserved:** expected business failures travel as `Esito` values, never exceptions;
  adding an error to a context breaks compilation where that context's errors are shown.
- **`enforced_by` replaced:** the original rule matched only a `sealed … ErroreDominio` declaration
  in `kernel/` (a form that can no longer exist). The new rule scans all modules and fails if
  `ErroreDominio`, or any `Errore<X>` type, appears on a single-line declaration together with an
  `Exception`/`Throwable`/`Error` supertype — the kernel declaration or any context subtype.
  Multi-line declarations are covered by Konsist (CR-8).

## Amendment 2026-09-23 (b) — error PLACEMENT: rules and lookups in `dominio`, technical failures in `applicazione`
**Why.** The first amendment said each context owns ONE sealed hierarchy "in its `dominio`, or
`applicazione` for application-only errors". Read together, "one per context" and "or
`applicazione`" were ambiguous — is a lookup miss raised by an application service
"application-only"? — and the build produced divergent placements. Clarified with the user on
2026-09-23 **[user]** (precedent: user decision D1 on `ErroreApplicazioneProgetto`).

**Amended decision (replaces the placement clause of the first amendment; everything else stands).**
- **Domain-rule errors AND lookup misses** — every `…NonTrovato` / `…GiaPresente`, even when only an
  application service can detect it (e.g. `ErroreProgetto.ProgettoGiaPresente`,
  `ErroreProgetto.RegistrazioneNonTrovata`, `ErroreTrascrizione.RegistrazioneNonTrovata`,
  `ErroreTrascrizione.TrascrittoNonTrovato`, `ErroreParlanti.ParlanteNonTrovato`) — go in the
  context's **single** sealed `Errore<Contesto>`, file **`:<ctx>:dominio/…/Errori<Contesto>.kt`**
  (package `snastro.<ctx>.dominio`).
- A **second** sealed hierarchy **`ErroreApplicazione<Contesto>`** in `:<ctx>:applicazione` is allowed
  **only for technical/adapter failures** surfaced as expected outcomes (source unreadable, format
  unsupported, copy failed …) — never for rules or lookups. At most **ONE per `applicazione`
  module**, in ONE file `ErroriApplicazione<Contesto>.kt`, package **`snastro.<ctx>.applicazione.porte`**
  (next to the ports whose adapters raise them). Precedent, as it exists in the code:
  `progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/porte/ErroriApplicazioneProgetto.kt`
  — `sealed interface ErroreApplicazioneProgetto : ErroreDominio { AudioNonLeggibile; FormatoNonSupportato; CopiaFallita }`.
- **In short:** one sealed hierarchy per context for rules/lookups + at most one per `applicazione`
  module for technical failures. Technical modules returning `Esito` outside a context (e.g.
  `:modelli`) keep their one `Errore<Modulo>` as in the first amendment.
- Both hierarchies are direct subtypes of `ErroreDominio`, named `Errore…` (CR-8 Konsist), and each
  gets its own exhaustive `messaggioPer` in `MessaggiErrore.kt` plus a branch in the
  `ErroreDominio` dispatcher (RC-4).
- `enforced_by` unchanged; the placement is checked by code-review (CR-8), not by a grep.
