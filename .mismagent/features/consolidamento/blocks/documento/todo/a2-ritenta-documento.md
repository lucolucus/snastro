---
id: a2-ritenta-documento
type: adapter
context: documento
side: app
wave: 3
release: R3c
module: ":documento:adattatori (eventi/AbbonatoDocumentoEventi) + its build file (implementation :supporto); :avvio wiring of a Segnalazione"
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
Replace AbbonatoDocumentoEventi's private conflated-channel + backoff loop with a RitentaConBackoff keyed by Registrazione, whose job maps the regeneration outcome to Boolean and whose Segnalazione (injected by :avvio, JUL-backed) reports every failure and recovery. A poisoned Registrazione is reported and retried without blocking the others; CancellationException and Error are rethrown.

## Tasks
- AC-C45 AbbonatoDocumentoEventi contains no Channel(CONFLATED) loop, no hand-written backoff and no runCatching: its background work is one RitentaConBackoff whose key is the Registrazione's id (String)
- AC-C46 A regeneration of Registrazione X that fails permanently → the injected recording Segnalazione receives one report per failed attempt naming X with the cause; meanwhile an event for Registrazione Y is regenerated (Y's Documento written while X still fails)
- AC-C47 The startup sweep does not stop at a poisoned Registrazione: with X failing, every other Registrazione of the project is regenerated
- AC-C48 Cancelling the scope stops the loop with no report and no further regeneration; an Error thrown by the regeneration escapes to the scope's exception handler instead of being retried
- AC-C49 The existing AbbonatoDocumentoEventi ACs stay green and run on virtual time (runTest): no test waits the real 30 s backoff

## Dependencies
- `supporto-api` (consumes it; owner `a0-supporto-moduli`) — consumers: `b1-lettura-coerente-primitiva`, `a1-supporto-test-adozione`, `a2-ritenta-documento`, `a3-ritenta-parlanti`, `a4-supporto-avvio`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `Segnalazione (snastro.supporto)`: public fun interface Segnalazione { public fun segnala(messaggio: String, causa: Throwable?) } — the ONLY logging channel; :supporto never touches JUL; :avvio injects the JUL-backed implementation
  - pinned `RitentaConBackoff (snastro.supporto)`: public class RitentaConBackoff<K>(lavoro: suspend (K) -> Boolean /* true = done, false = retry */, segnalazione: Segnalazione, attesaIniziale: Duration, attesaMassima: Duration) { public fun avvia(scope: CoroutineScope): Job; public fun richiedi(chiave: K) } — keys coalesce over a conflated signal; bounded exponential backoff (doubling from attesaIniziale, capped at attesaMassima); segnala on every failure (key + cause, cause null for a false return) and once on recovery; a failed key is retried until success or scope cancel; rethrows CancellationException and every Error, never runCatching around them (constructor shape CONFIRMED by the user, checkpoint 2026-09-27)
  - pinned `gestoreErroriNonCatturati (snastro.supporto)`: public fun gestoreErroriNonCatturati(segnala: Segnalazione): CoroutineExceptionHandler
  - pinned `figlioDi (snastro.supporto)`: public fun figlioDi(genitore: CoroutineScope, dispatcher: CoroutineDispatcher? = null, gestore: CoroutineExceptionHandler): CoroutineScope — child scope with a SupervisorJob tied to the parent's Job
  - pinned `catturaNonFatale (snastro.supporto)`: public inline fun <T> catturaNonFatale(blocco: () -> T): Result<T> — rethrows CancellationException and VirtualMachineError, catches everything else; the one sanctioned catch-all at the edges (CR-7)
  - pinned `attendiFinche (snastro.supporto.test)`: public fun attendiFinche(timeout: Duration = 5.seconds, messaggio: String, condizione: () -> Boolean) — polls with a short sleep, fails with AssertionError(messaggio)
  - pinned `OrologioFinto (snastro.supporto.test)`: public class OrologioFinto(inizio: Instant, zona: ZoneId = ZoneOffset.UTC) : java.time.Clock { public fun avanza(durata: Duration); public fun imposta(istante: Instant) }
  - pinned `conScopeDiProva (snastro.supporto.test)`: public fun <T> conScopeDiProva(blocco: (CoroutineScope) -> T): T — runs the block and cancels the scope in finally; plus StandardTestDispatcher helpers for runTest + backgroundScope
  - pinned `excluded (ADR 0028 §4)`: no context type, id, Esito, ErroreDominio or business rule; no claim/run/complete template; EsitoAtteso stays in :kernel testFixtures
  - key `K (RitentaConBackoff key)`: minted by the CONSUMER block, never by :supporto — a2: the Registrazione id as String (RegistrazioneId.valore, UUID v4 minted by AggiungiRegistrazione, immutable); a3: the key AbbonatoRiallineamentoImpronte coalesces on today, unchanged; coalescing uses equals()

## Notes
PRE-RELEASE (features/sintesi/pre-release.md): closes line 171 (permanent exception retried every 30 s with no log; sweep stops at the poisoned Registrazione — AC-C46/AC-C47) and line 172 (runCatching around CancellationException/Error — AC-C48; its atomicity-test half was done by b2 AC-C36, so a2 ticks the whole line). Until a4 unifies it, :avvio passes a JUL-backed Segnalazione at the current wiring site (EstensioneR1); a4 replaces it with the single one. The subscriber keeps registering as today: moving it to values + avvia(scope) is c3.

Sources: ADR 0028 §2, §7.3, ADR 0012 (retry wording note), analisi-design.md §2.4 X5, tactical-model.md (no domain change)
