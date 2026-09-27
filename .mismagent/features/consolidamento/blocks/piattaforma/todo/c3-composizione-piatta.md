---
id: c3-composizione-piatta
type: adapter
context: piattaforma
side: app
wave: 4
release: R3c
module: ":avvio (snastro.avvio.progetto|trascrizione|parlanti|sintesi|documento|modelli|coda|smoke; Main.kt stays in snastro.avvio), the subscribers in */adattatori/eventi (values + avvia(scope)), :avvio tests (one AmbienteProgetto), :architettura-test (controlli-adr)"
title: "Composizione piatta per contesto: moduli, abbonati come valori, apriProgetto ordinato, Campanello, CollaboratoriProgetto tipato, ArrestoProgetto; ritiro di R0–R2"
consumes:
  - lettura-coerente
  - supporto-api
related_adrs:
  - "0012"
  - "0017"
  - "0020"
  - "0021"
  - "0023"
  - "0024"
  - "0028"
  - "0030"
model_hint: deep
tests_nl_status: confirmed
---
# c3-composizione-piatta

## What to do
Replace the release-layered root with the single composition of ADR 0030: ModuloTrascrizione/Parlanti/Sintesi/Documento built from PorteProgetto expose subscriber VALUES, queue sources, avvia(scope)/ferma() and typed collaborators; apriProgetto registers the synchronous subscribers from one declared list Sintesi → Parlanti → Trascrizione, then the after-commit ones, builds CodaCondivisa woken through a Campanello, runs the recoveries and starts everything; CollaboratoriProgetto is typed; one ArrestoProgetto stops in reverse with one deadline; packages go by concern. R0–R2 are retired as code, the ADR 0030 §3 AC table is applied, the remaining R-tests keep their ids on one AmbienteProgetto, and the ADR 0030 script is delivered and activated. May ship as two PRs (subscribers-as-values, then the flat root) but is one block.

## Tasks
- AC-C67 No adapter registers itself: no `DispatcherEventiInMemoria` import in any */adattatori/src/main file (6 files today) and no subscriber registration in an init block; each adapter exposes AbbonatoSincrono/AbbonatoDopoCommit values; AbbonatoDocumentoEventi and AbbonatoRiallineamentoImpronte launch nothing until avvia(scope) (a test constructs one and observes no running job)
- AC-C68 ModuloTrascrizione, ModuloParlanti, ModuloSintesi and ModuloDocumento are built from PorteProgetto only (none builds a repository) and each exposes abbonatiSincroni(), abbonatiDopoCommit(), fontiCoda(), avvia(scope), ferma() and its typed collaborators
- AC-C69 AC-S143 rewritten (keeps its id): it asserts the declared synchronous list is exactly [Sintesi, Parlanti, Trascrizione] and that registration precedes CodaCondivisa's construction and any command — with NO reflection on `sincroni` and no source-text scan; swapping two entries of the list makes it FAIL (probe, not committed)
- AC-C70 apriProgetto builds CodaCondivisa from the fontiCoda() of every module and a Campanello created before the modules; a Riassunto enqueued by ModuloSintesi wakes and runs; `AtomicReference<CodaCondivisa` does not appear in avvio/src/main
- AC-C71 The recoveries of every source run before the first claim (AC-S145 green on the single composition)
- AC-C72 CollaboratoriProgetto(trascrizione, parlanti, sintesi, documento) has non-null typed fields; avvio/src/main has no `as Collaboratori…` / `as? Collaboratori…` cast and no `r3.r2.r1` chain
- AC-C73 ArrestoProgetto(scadenza) stops the queue and the modules in the reverse order of avvia (recording fakes) under ONE shared deadline: a module whose ferma blocks past it does not stretch the total shutdown beyond scadenza plus a small margin; ADR 0017 §3 shutdown tests green; every per-project scope is figlioDi(scopeProgetto, …)
- AC-C74 Retired, absent from src/main and src/test: costruisciGrafoR1/R2, GrafoR1/R2/R3, ContenutoAppR1/R2/R3 and R0's ContenutoApp, EstensioneSessione's release chaining, EstensioneR1/R2/R3, CollaboratoriR1/R2/R3, LettoreNomiVuoto, SEZIONI_SHELL_R0/R1; there is one Grafo (GrafoR0 flattened in), one ContenutoApp, one SEZIONI_SHELL, one io dispatcher and one merge of AggiornamentiVista
- AC-C75 One ProvisioningModelli per app: under --smoke the swapped models service and DisponibilitaModelloLinguisticoAvvio use the SAME ProvisioningModelli instance (===); SmokeTest green
- AC-C76 Packages by concern: no directory avvio/src/main/kotlin/snastro/avvio/r<digit> exists; ErroreApplicazioneAvvio, ServizioModelliProvisioning, SelezioneAdattatoriMl, SchermataR1 and the ADR 0019 §4.1 glue (LavoriPerChiave, ProposteSerializzate, AzioniSomiglianzaProgetto → avvio.parlanti) sit in their concern package; Main.kt stays in snastro.avvio
- AC-C77 ADR 0030 §3 deletions applied: GrafoR0Test's four AC-350 cases, AC-356 'nessuna classe di parlanti e referenziata dalla composizione R1' and AC-S143 'la composizione R2 non mostra la scheda Riassunto…' are gone; AggiungiRegistrazioneR0Test's AC-350 is merged into AC-371
- AC-C78 ADR 0030 §3 retargets green on the single composition, ids kept: AC-371 (after an import no Elaborazione exists, the view is NON_AVVIATA, S2 shows Trascrivi); AC-355 (no synchronous subscriber on RegistrazioneAggiunta, asserted on the declared lists; every command service gets the dispatcher's unit of work and LetturaCoerente is that same UnitaDiLavoroSql); AC-356 (a Voce with no named Parlante renders as `Voce n`; a committed Revisione regenerates the Documento)
- AC-C79 Every other AC under avvio/src/test/.../r1|r2|r3 keeps its id, moves to the per-concern test package and runs on ONE AmbienteProgetto built through the production apriProgetto (AmbienteR1/R2/R3 deleted): the set of `AC-<n>` test-name ids in :avvio after the block equals the set before minus exactly the §3 deletions (script diff attached)
- AC-C80 architettura-test/controlli-adr/adr-0030-composizione-unica.sh exists with red-green fixtures for its four clauses (an r<digit> directory; an `as`/`as? Collaboratori` cast; `AtomicReference<CodaCondivisa`; a file outside `<ctx>/` and `progetto/` importing or fully-qualifying snastro.<ctx>.adattatori), validated via ControlliAdrTest, exits 0 on the tree; ADR 0030's PLANNED comment becomes a real `- check:` + `from: c3-composizione-piatta` entry; adr-0023-posizione-in-coda-fuori-dai-contesti.sh stays green

## Dependencies
- `lettura-coerente` (consumes it; owner `b1-lettura-coerente-primitiva`) — consumers: `b2-lettura-coerente-migrazione`, `c1-porte-progetto`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `LetturaCoerente (:kernel, snastro.kernel)`: public interface LetturaCoerente { public fun <T> inLettura(blocco: () -> T): T } — ONE consistent snapshot, read-only; infra faults throw (ADR 0003), no Esito
  - pinned `UnitaDiLavoro (:kernel, unchanged)`: public interface UnitaDiLavoro { public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — signature and UnitaDiLavoroContratto unchanged
  - pinned `semantics (ADR 0029 §2, binding)`: one per-thread state shared by both ports (depth, mode none|write|read, doom flags): 1 outermost inLettura → BEGIN DEFERRED + PRAGMA query_only = 1; 2 inLettura in inTransazione joins, sees uncommitted writes; 3 inLettura in inLettura joins; 4 inTransazione in inLettura → IllegalStateException before any effect; 5 a write in a read fails hard (SQLITE_READONLY / Finta refuses); 6 an exception in a nested inLettura dooms the enclosing inTransazione; 7 no leak: query_only 0 and mode none before the connection goes back
  - pinned `UnitaDiLavoroSql (:persistenza)`: public class UnitaDiLavoroSql(db: SnastroDatabase) : UnitaDiLavoro, LetturaCoerente — its ThreadLocal<Stato> gains the mode; the composition passes the SAME instance as the project's LetturaCoerente and as DispatcherEventiInMemoria's delegate; the dispatcher's wrapper does not wrap LetturaCoerente
  - pinned `DriverSqliteImmediato (:persistenza)`: per-thread begin mode, IMMEDIATE by default (writes byte-identical), DEFERRED only for the outermost read set by UnitaDiLavoroSql; the slot-clearing on a failed BEGIN applies to both modes
  - pinned `checkpointDopoCommit (:persistenza, snastro.persistenza)`: public fun SnastroDatabase.checkpointDopoCommit() — registers PRAGMA wal_checkpoint(TRUNCATE) after the current transaction commits (joins the caller's transaction, as ParlanteRepositorySql:84 does today); dropped on rollback; one checkpoint per call
  - pinned `LetturaCoerenteContratto (:kernel testFixtures)`: abstract class; Ambiente { lettura: LetturaCoerente; unitaDiLavoro: UnitaDiLavoro /* same state */; scrivi(effetto: String); effetti(): List<String>; leggi(): Set<String> /* through lettura */ }; cases 1–7 of ADR 0029 §4; subclasses: Finta (:kernel), UnitaDiLavoroSql and UnitaDiLavoroSql behind DispatcherEventiInMemoria.unitaDiLavoro (:persistenza)
  - pinned `UnitaDiLavoroFinta (:kernel testFixtures)`: ONE class implementing UnitaDiLavoro and LetturaCoerente (one depth counter, one mode); val transazioneAperta (unchanged), val letturaAperta; refuses scrivi while a read is open
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
PRE-RELEASE (features/sintesi/pre-release.md): closes line 56 and line 143 (two ProvisioningModelli on the same folder / --smoke swap — AC-C75), line 179 (completes it: AC-S143 reflection + source-text order — AC-C69; a1 did the sleep half, Q1 the timeout half), line 183 (completes it after c2: the order note is ADR 0030 §2 / ADR 0024 §4; add the 'esclusa' queue log for Riassunto ids), lines 173 and 174 (the D-0008 drain lives in ContenutoAppR1/R2FooterModelloTest, retired or retargeted here — remove the drain, a leak must fail the test). CHECK-AND-TICK (verify, tick with a pointer only if true on the single composition, else leave open with a note): line 131 (ONE SelezioneSchedaS3 per window shared across RegistrazionePresenter instances, AC-S121), lines 94 and 101 (per-run annullato flag vs a late RiassuntoEliminato — the fix was assigned to avvio-sintesi; confirm it survives in ModuloSintesi). CONTEXT: ADR 0030 §4 lists 'the single test Ambiente' under c4, but §3 and dev-architecture §10 require the R-tests to run on AmbienteProgetto when R0–R2 retire; this manifest puts AmbienteProgetto here (AC-C79) and leaves to c4 only the :ui side — see the report's open question. The ADR 0030 §3 table is the record of the retirement; earlier features' manifests are NOT edited.

Sources: ADR 0030 §1–§4 + enforced_by (PLANNED), ADR 0024 §4, ADR 0021 §10, ADR 0028 §7.5, ADR 0029 §3, dev-architecture-app.md §10 #composizione, architecture.md (§ Composition), analisi-design.md §2.3 R1–R4, §2.4 X4, tactical-model.md (no domain change)
