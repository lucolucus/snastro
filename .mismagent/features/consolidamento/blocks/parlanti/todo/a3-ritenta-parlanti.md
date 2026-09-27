---
id: a3-ritenta-parlanti
type: adapter
context: parlanti
side: app
wave: 3
release: R3c
module: ":parlanti:adattatori (eventi/AbbonatoRiallineamentoImpronte) + build file (implementation :supporto); :avvio r2 (PorteImprontaConLog retired, wiring)"
title: "AbbonatoRiallineamentoImpronte su RitentaConBackoff; ritiro di PorteImprontaConLog"
consumes:
  - supporto-api
related_adrs:
  - "0012"
  - "0019"
  - "0028"
tests_nl_status: draft
---
# a3-ritenta-parlanti

## What to do
Move AbbonatoRiallineamentoImpronte's duplicated worker onto RitentaConBackoff with an injected Segnalazione, so failures and recoveries are reported by the hook, and retire avvio/r2/PorteImprontaConLog whose only purpose was to log them.

## Tasks
- AC-C50 AbbonatoRiallineamentoImpronte contains no private conflated loop, backoff or runCatching: its background work is one RitentaConBackoff
- AC-C51 A riallineamento failing for key K is reported via the injected recording Segnalazione (K + cause) and retried; its later success reports the recovery; another key K2 is processed while K fails
- AC-C52 PorteImprontaConLog.kt is deleted and nothing in src/main or src/test references it; the failure it used to log is now reported by the Segnalazione (a test with a failing EstrattoreImpronta Finta observes the report)
- AC-C53 Cancellation stops the loop with no report; an Error escapes to the scope's handler instead of being retried; the existing Parlanti ACs (riallineamento, ADR 0019) stay green

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
PRE-RELEASE (features/sintesi/pre-release.md): closes no line (the Parlanti worker duplicate is analysis X5, not a pre-release finding). Same Segnalazione wiring as a2 until a4.

Sources: ADR 0028 §2, §7.4, ADR 0019 (riallineamento), analisi-design.md §2.4 X5, tactical-model.md (no domain change)
