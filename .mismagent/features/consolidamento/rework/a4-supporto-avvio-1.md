# Rework a4-supporto-avvio — cycle 1 (verifier FAIL on 5cad5d2)
Gate green; AC-C57/C88/C90-smoke probes go red as they should. Five FAILs:

## 1. HIGH (production correctness, AC-C87/C89) — the log file can silently stay empty
CartellaLogApp.kt:60 attaches the FileHandler to `Logger.getLogger("snastro")` held only in a local; JUL holds loggers
WEAKLY and segnalazioneApp re-fetches the logger per call. A GC in the startup window collects the "snastro" logger and
its handler (verifier probe: configuraLoggingApp(tmp) → System.gc() ×20 → segnala → handlers 1→0, record not in the
file). The tests hide it by holding the logger in a field. Fix: a strong top-level reference (the logger or a
handler-holding val) + a regression test that calls gc() between configure and report and asserts the record is in the
file.

## 2. AC-C55 — the per-project Documento/Parlanti scopes are untested
The only test injects `c.scope.launch { error(…) }` on the session scope, not the "fault-injected after-commit job" the
AC names. Probe: giving EstensioneR1's Documento worker scope a swallowing handler leaves every test green. Add a test
that fault-injects an after-commit job on the per-project scope (Documento worker; ideally Parlanti too) and asserts
exactly one report carrying THAT throwable.

## 3. AC-C90 — the user.home assertion is missing
The AC requires "no write to the real user folder — asserted by pointing user.home at a temp dir". Add it (for the
:avvio tests and --smoke): with user.home pointed at a temp dir, nothing is created under the real per-user log folder
path; the existing pure-resolver test does not prove this.

## 4. AC-C54 as worded — `segnalaBloccato` must use the one Segnalazione too
"the Documento, Parlanti and queue hooks all receive it": the queue has two hooks; `segnalaBloccato` (EstensioneR1.kt:122)
still logs via a local log.warning. Route it through segnalazioneApp. An exclusion must be logged at WARNING (not INFO
like a recovery): the pinned `Segnalazione.segnala(messaggio, causa)` signature must NOT change — e.g. pass a
descriptive cause for the exclusion, or another approach that does not string-match messages. If impossible without a
pin change, STOP and return BOUNCED.

## 5. AC-C58 — "the shutdown still completes (DB closed, .lock released)" not asserted
The interrompi test only checks fermaEAttendi(...) == true. Assert the end state at the SessioneProgettoImpl level
(e.g. lockTenuto == false and the DB closed) when interrompi throws.
