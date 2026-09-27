---
id: a4-supporto-avvio
type: adapter
context: piattaforma
side: app
wave: 3
release: R3c
module: ":avvio (Main: rotating JUL FileHandler; Segnalazione JUL, gestoreErroriNonCatturati, figlioDi at SessioneProgettoImpl/ContenutoAppR1/CollaboratoriR2/ComandiVoceProgetto/AzioniSomiglianzaProgetto/EstensioneR1/EstensioneR2, CodaCondivisa)"
title: ":avvio su :supporto — log JUL su file a rotazione, una Segnalazione JUL, un gestore degli errori non catturati, figlioDi, CodaCondivisa segnalaSfuggito e rilancio di VirtualMachineError"
consumes:
  - supporto-api
related_adrs:
  - "0017"
  - "0023"
  - "0028"
tests_nl_status: draft
---
# a4-supporto-avvio

## What to do
Configure JUL once in :avvio's Main with a rotating FileHandler in the per-user app log folder (analysis X9), give :avvio ONE JUL-backed Segnalazione that writes through it, one gestoreErroriNonCatturati built on it per open project, and replace every hand-built CoroutineScope(parent + SupervisorJob(...)) child scope with figlioDi. CodaCondivisa gains a segnalaSfuggito hook wired to the Segnalazione, reports a run's escaped throwable, rethrows VirtualMachineError, and stops swallowing throwables of interrompi.

## Tasks
- AC-C54 :avvio src/main has exactly ONE Segnalazione implementation (JUL-backed); the Documento, Parlanti and queue hooks all receive it (the a2/a3 local ones are gone)
- AC-C55 One gestoreErroriNonCatturati per open project: an exception escaping a per-project coroutine (fault-injected after-commit job) is reported exactly once via the Segnalazione and cancels neither the project scope nor its sibling jobs
- AC-C56 No `CoroutineScope(… + SupervisorJob(…))` remains in avvio/src/main: the seven hand-built child scopes (SessioneProgettoImpl, ContenutoAppR1, CollaboratoriR2, ComandiVoceProgetto, AzioniSomiglianzaProgetto, EstensioneR1, EstensioneR2) use figlioDi; the root scope of GrafoR0 is not a child and stays
- AC-C57 CodaCondivisa: a run throwing RuntimeException → segnalaSfuggito called once with it and the queue goes on with the next item (AC-S61 green); a run throwing StackOverflowError is rethrown — not swallowed, reported through the gestore — and the queue claims no further item
- AC-C58 CodaCondivisa: an interrompi that throws inside fermaEAttendi is caught with catturaNonFatale and reported, and the shutdown still completes (DB closed, .lock released); a throwing ultimaTentata()/segnalaBloccato is caught and reported too
- AC-C59 The KDoc claim 'No logging sink exists in this codebase' is gone; adr-0023-posizione-in-coda-fuori-dai-contesti.sh and every existing CodaCondivisa AC stay green
- AC-C87 Main configures ONE rotating java.util.logging.FileHandler on the snastro root logger, writing to the per-user app log folder `<app-data folder resolved like CartellaDatiRegistroProgetti>/log` (macOS: ~/Library/Application Support/snastro/log), created if missing; the folder resolution is a pure function (os/env/user.home injected) table-tested for macOS, Windows and Linux/XDG, and never points inside a project folder
- AC-C88 Rotation: with the size bound and file count injected small in a test (e.g. 1 KB, 3 files), writing well over 3 KB of records leaves exactly 3 files (snastro.0.log newest … snastro.2.log) each ≤ the bound plus one record, and the oldest content is gone; the production values are named constants (limit and count) in one place
- AC-C89 End to end: a report through the app's Segnalazione (e.g. a failing RitentaConBackoff job, or CodaCondivisa segnalaSfuggito) appears in the log file with its message and the cause's stack trace, after the handler is flushed
- AC-C90 The file handler never breaks --smoke or headless runs: under `--smoke` (SmokeTest) and in every :avvio test the log goes to a temp/injected folder or is not installed (no write to the real user folder — asserted by pointing user.home at a temp dir), an unwritable log folder falls back to console logging with one warning instead of failing startup, and the gate stays green

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
PRE-RELEASE (features/sintesi/pre-release.md): line 124 is PARTIAL here — AC-C58 covers 'interrompi throwing escapes fermaEAttendi' and 'ultimaTentata()/segnalaBloccato outside eseguiProtetto'; its other sub-items (sources looked up by kind with firstOrNull, enumeraTutti O(N²)/not one snapshot) are NOT in scope: do not tick 124, append ' · partial: a4 fixed interrompi/segnalaBloccato' to it. The per-project scopes move again in c3 (ADR 0028 §7.5 note). The rotating JUL FileHandler (analysis X9, ADR 0028 §2) was ADDED here by the user checkpoint 2026-09-27 (AC-C87..AC-C90). It closes no pre-release line by itself: it makes line 184 (every DownloadFallito shown as 'connessione interrotta') diagnosable from a user's log, but 184's fix is the error mapping (analysis X7), not in this feature — leave 184 open.

Sources: ADR 0028 §2, §7.5, ADR 0023 (queue note), ADR 0017 §3, analisi-design.md §2.3 R3, §2.4 X8/X9/X11, tactical-model.md (no domain change)
