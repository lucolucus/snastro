---
id: a2-ritenta-documento
type: adapter
context: documento
side: app
wave: 3
release: R3c
module: ":documento:adattatori (eventi/AbbonatoDocumentoEventi) + its build file (implementation :supporto); :supporto (RitentaConBackoff.kt foreign-cancellation fix + RitentaConBackoffTest); :avvio wiring of a Segnalazione"
title: "AbbonatoDocumentoEventi su RitentaConBackoff: niente retry silenzioso, rilancio di cancellazioni ed errori fatali"
consumes:
  - supporto-api
related_adrs:
  - "0010"
  - "0012"
  - "0028"
tests_nl_status: draft
---
# a2-ritenta-documento

## What to do
First fix :supporto's RitentaConBackoff so that a CancellationException not caused by the worker's own cancellation (withTimeout, await on a cancelled Deferred inside lavoro) is a failure — reported and retried — and only the worker's own cancellation is rethrown. Then replace AbbonatoDocumentoEventi's private conflated-channel + backoff loop with ONE RitentaConBackoff over ONE sealed key type covering its three unit kinds (per Registrazione, per ParlanteId, the startup sweep), so they stay serialized; the per-key payload map (LavoroPendente merge, removal wins, a failed payload re-merged first) stays in the adapter because RitentaConBackoff coalesces keys only. The job maps the outcome to Boolean; the Segnalazione (injected by :avvio, JUL-backed) reports every failure and recovery; Error is rethrown.

## Tasks
- AC-C45 AbbonatoDocumentoEventi contains no Channel(CONFLATED) loop, no hand-written backoff and no runCatching: its background work is ONE RitentaConBackoff instance whose key is one private sealed type with exactly three cases — per Registrazione (RegistrazioneId), per Parlante (ParlanteId) and the startup sweep (a data object)
- AC-C46 A regeneration of Registrazione X that fails permanently → the injected recording Segnalazione receives one report per failed attempt naming X with the cause; meanwhile an event for Registrazione Y is regenerated (Y's Documento written while X still fails)
- AC-C47 The startup sweep does not stop at a poisoned Registrazione: with X failing, every other Registrazione of the project is regenerated
- AC-C48 Cancelling the scope stops the loop with no report and no further regeneration; an Error thrown by the regeneration escapes to the scope's exception handler instead of being retried
- AC-C49 The existing AbbonatoDocumentoEventi ACs stay green and run on virtual time (runTest): no test waits the real 30 s backoff
- AC-C91 (:supporto, RitentaConBackoffTest) a lavoro that throws a FOREIGN CancellationException — withTimeout expiring inside it, or awaiting an already-cancelled Deferred — while the worker's scope is still active is reported through segnala (key + that exception) and retried with the backoff, and a key requested afterwards still runs; the rethrow applies only when the worker's own context is no longer active. AC-C8's test is adjusted to cancel the worker's OWN scope and still passes (no segnala, no further run); the public API is unchanged, so CR-18c stays green
- AC-C92 The three unit kinds never run concurrently: with a job that blocks on a latch for a PerRegistrazione key, a PerParlante key and the sweep requested meanwhile do not start until it is released (a probe counting in-flight jobs never exceeds 1), because they share the single RitentaConBackoff instance
- AC-C93 Removal wins over a pending update: RegistrazioneRinominata then RegistrazioneEliminata for the same Registrazione, both merged before its run, produce ONE run that removes the Documento (with the renamed title among nomiPrecedenti) and writes nothing; an update merged after a pending removal does not turn it back into a write (AC-624 stays green)
- AC-C94 A failed payload is merged back and retried: a DataRegistrazioneModificata run whose write fails, while a RegistrazioneRinominata for the same Registrazione arrives during the attempt, is retried ONCE with both precedenti (the failed payload merged first, primaArrivata) and succeeds; the key is re-requested only through RitentaConBackoff's retry, not by a second loop

## Dependencies
- `supporto-api` (consumes it; owner `a0-supporto-moduli`) — consumers: `b1-lettura-coerente-primitiva`, `a1-supporto-test-adozione`, `a2-ritenta-documento`, `a3-ritenta-parlanti`, `a4-supporto-avvio`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `Segnalazione (snastro.supporto)`: public fun interface Segnalazione { public fun segnala(messaggio: String, causa: Throwable?) } — the ONLY logging channel; :supporto never touches JUL; :avvio injects the JUL-backed implementation
  - pinned `RitentaConBackoff (snastro.supporto)`: public class RitentaConBackoff<K>(lavoro: suspend (K) -> Boolean /* true = done, false = retry */, segnalazione: Segnalazione, attesaIniziale: Duration, attesaMassima: Duration) { public fun avvia(scope: CoroutineScope): Job; public fun richiedi(chiave: K) } — keys coalesce over a conflated signal; bounded exponential backoff (doubling from attesaIniziale, capped at attesaMassima); segnala on every failure (key + cause, cause null for a false return) and once on recovery; a failed key is retried until success or scope cancel; rethrows every Error, and a CancellationException ONLY when the worker's own context is no longer active — a foreign one raised inside lavoro (withTimeout, await on a cancelled Deferred) is a failure: reported and retried (fixed by a2, delta 2 2026-09-27); never runCatching around them (constructor shape CONFIRMED by the user, checkpoint 2026-09-27)
  - pinned `gestoreErroriNonCatturati (snastro.supporto)`: public fun gestoreErroriNonCatturati(segnala: Segnalazione): CoroutineExceptionHandler
  - pinned `figlioDi (snastro.supporto)`: public fun figlioDi(genitore: CoroutineScope, dispatcher: CoroutineDispatcher? = null, gestore: CoroutineExceptionHandler): CoroutineScope — child scope with a SupervisorJob tied to the parent's Job
  - pinned `catturaNonFatale (snastro.supporto)`: public inline fun <T> catturaNonFatale(blocco: () -> T): Result<T> — rethrows CancellationException and VirtualMachineError, catches everything else; the one sanctioned catch-all at the edges (CR-7)
  - pinned `attendiFinche (snastro.supporto.test)`: public fun attendiFinche(timeout: Duration = 5.seconds, messaggio: String, condizione: () -> Boolean) — polls with a short sleep, fails with AssertionError(messaggio)
  - pinned `OrologioFinto (snastro.supporto.test)`: public class OrologioFinto(inizio: Instant, zona: ZoneId = ZoneOffset.UTC) : java.time.Clock { public fun avanza(durata: Duration); public fun imposta(istante: Instant) }
  - pinned `conScopeDiProva (snastro.supporto.test)`: public fun <T> conScopeDiProva(blocco: (CoroutineScope) -> T): T — runs the block and cancels the scope in finally; plus StandardTestDispatcher helpers for runTest + backgroundScope
  - pinned `excluded (ADR 0028 §4)`: no context type, id, Esito, ErroreDominio or business rule; no claim/run/complete template; EsitoAtteso stays in :kernel testFixtures
  - key `K (RitentaConBackoff key)`: minted by the CONSUMER block, never by :supporto; coalescing uses equals(), and RitentaConBackoff coalesces KEYS only (no payload) — a2: ONE private sealed key type on ONE instance (so all Documento units stay serialized): PerRegistrazione(RegistrazioneId — UUID v4 minted by AggiungiRegistrazione, immutable) | PerParlante(ParlanteId — minted by Parlanti, immutable) | the startup sweep (data object); the per-key payload map (LavoroPendente merged by primaArrivata, a removal always wins, a failed payload re-merged first) stays in the adapter; a3: RegistrazioneId (the key AbbonatoRiallineamentoImpronte coalesces on today), no payload

## Notes
BY DESIGN this block touches a0's code (:supporto RitentaConBackoff.kt:80-81 and its test) — a0 is integrated and its row is unchanged; AC-C8 is read as 'the worker's OWN cancellation' from here on. PRE-RELEASE (features/consolidamento/pre-release.md): closes line 1 (a0 MED foreign CancellationException — AC-C91) and line 15 (a2 MED spec, supporto-api pin K — AC-C45/AC-C92..C94 + the re-pinned K). PRE-RELEASE (features/sintesi/pre-release.md): closes line 171 (permanent exception retried every 30 s with no log; sweep stops at the poisoned Registrazione — AC-C46/AC-C47) and line 172 (runCatching around CancellationException/Error — AC-C48; its atomicity-test half was done by b2 AC-C36, so a2 ticks the whole line). Until a4 unifies it, :avvio passes a JUL-backed Segnalazione at the current wiring site (EstensioneR1); a4 replaces it with the single one. The subscriber keeps registering as today: moving it to values + avvia(scope) is c3.

Sources: ADR 0028 §2, §7.3, ADR 0012 (retry wording note), analisi-design.md §2.4 X5, tactical-model.md (no domain change)
