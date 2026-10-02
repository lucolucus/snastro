# Rework 2 (LAST cycle) — adattatori-sbobinatura-incontro

Reviewed HEAD: 5fe350606aba429a9f87c9b804db6ac0d06945d8 · verifier FAIL (semantic-high). Rework 1 fixed AC-183 but introduced the defect below.

## FAIL — one failing Incontro blocks every Sbobinatura regeneration
- AbbonatoSbobinaturaEventi.kt:226 `incontriPendenti.toList().forEach(::fanOutParti)` runs at the start of EVERY PerRegistrazione run, unguarded.
- If `partiDellIncontro` keeps throwing for one Incontro X (e.g. a corrupt VociDellIncontro load), every PerRegistrazione run of every Registrazione — other Incontri and the startup sweep included — drains X first, throws, and fails before `pendenti.remove(id)`. No Sbobinatura in the app is regenerated while X is pending; retries forever; reported under the wrong key.
- Confirmed by the verifier with a probe: listing for X always throws, ElaborazioneCompletata(b, Incontro Y), 120 s virtual clock → 0 writes for b (expected 1).
- Breaks the isolation AC-C46/AC-C47 protect (one failing unit must not block the others).

## Required
- A Registrazione's run must not depend on another Incontro's listing succeeding, and must keep AC-183 (exactly one write for any ordering of a burst, duplicates included) and keep the listing off the committing thread, inside a retried unit, with no try/catch fallback in main (CR-19b).
- Direction (your choice): let the worker run pending PerIncontro keys BEFORE PerRegistrazione keys (ordering in the queue), instead of draining them inside each Registrazione's run; or drain only the Registrazione's OWN Incontro, with a failure there leaving X owed under Chiave.PerIncontro(X).
- Add the verifier's probe as a test: a listing that keeps failing for Incontro X leaves a Registrazione of Incontro Y written exactly once. AC-183 original-order test and the permutation test stay green. Show red-before on the new test.

Not in scope (pre-release.md): LOW — no test for a failure while draining inside a PerRegistrazione run (covered by the new test anyway); LOW — fanOutParti returns true on a no-op run (KDoc).
