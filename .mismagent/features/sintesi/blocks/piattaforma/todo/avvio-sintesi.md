---
id: "avvio-sintesi"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 6
release: "R3"
module: ":avvio (snastro.avvio.r3)"
consumes:
  - "coda-condivisa"
  - "riassunti-in-coda"
  - "eventi-sintesi"
  - "posizioni-nella-coda"
  - "ui-schede-registrazione"
  - "disponibilita-modello"
  - "tec-modello-linguistico"
  - "vista-riassunto"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
  - "trascrizione-con-parlanti/tec-shell-ui"
related_adrs:
  - "0012"
  - "0020"
  - "0021"
  - "0023"
  - "0024"
  - "0025"
ready_when: "SATISFIED 2026-09-26 — Elimina registrazione (ADR 0020) is on main (6daba4e) and integration/sintesi is rebased on it"
model_hint: "deep"
tests_nl_status: "draft"
---
# avvio-sintesi — Composizione R3 'Sintesi': abbonati, coda condivisa con la sorgente Riassunto, scheda Riassunto, e2e

## What to do
The R3 composition on top of R2 (ADR 0021 §10): registers all synchronous subscribers (RegistrazioneEliminata: Trascrizione, Parlanti, Sintesi; TrascrittoSostituito: Parlanti, Sintesi) BEFORE the first command; binds CodaCondivisa with the Elaborazione and Riassunto sources; maps Sintesi events after commit to Cambiamento / avanza() / best-effort cancellation; wires the Riassunto tab (schede-registrazione slot + scheda-riassunto) and the optional model; binds a placeholder ModelloLinguistico (every run → modello_non_disponibile) until modello-linguistico-llama replaces it; --smoke; end-to-end tests on real SQLite with fake ML ports.

Note: Release R3 = the set of blocks with release ≤ R3. The placeholder ModelloLinguistico keeps R3 buildable and demonstrable (tab, queue, download UI) before the spike; R3 is shippable only after modello-linguistico-llama. MANDATORY AC-S161 (user decision 2026-09-26, D-0004; folds pre-release MEDs esegui-riassunto EseguiProssimoRiassuntoServizio.kt:66,93 and sostituzione-trascritto-sintesi-policy cross-block): the single service-lifetime `annullato` flag of EseguiProssimoRiassuntoServizio (constructor `annullato: () -> Boolean`) must become per run, keyed by the running RiassuntoId and reset at each claim, and the RiassuntoEliminato → annullaInCorso mapping of AC-S144 is guarded by riassunti.trova(runningId) == null — a late/duplicate/coalesced Eliminato after sostituzione re-queued X must not leave X stuck in_corso. The AC-S161 guard lives in the Riassunto FonteCoda's `annulla` hook (7th pinned field of coda-condivisa, D-0005): CodaCondivisa.annullaInCorso(RIASSUNTO, registrazioneId) delegates to it, and it flips the per-run flag only if riassunti.trova(runningId) == null. STOP (D-0006, user 2026-09-26): the Riassunto FonteCoda also supplies `interrompi` (8th pinned field of coda-condivisa): CodaCondivisa.fermaEAttendi calls it on the running item's source and it flips the CURRENT run's per-run annullato flag UNCONDITIONALLY (no riassunti.trova guard) — an LLM run may not honour a thread interrupt (ADR 0023 §5, AC-S63); proven by AC-S162.

**ready_when:** SATISFIED 2026-09-26 — Elimina registrazione (ADR 0020) is on main (6daba4e) and integration/sintesi is rebased on it.

## Tasks
- AC-S143 The R3 graph registers the five synchronous subscribers before offering any command (test on the built graph: dispatcher inspection); R0–R2 compositions show no Riassunto tab and never create a riassunto row
- AC-S144 RiassuntoRichiesto → avanza(); every Sintesi event → Cambiamento(registrazioneId) after commit; LunghezzaMassimaRiassuntoModificata → Cambiamento(null); RiassuntoEliminato → annullaInCorso(RIASSUNTO, r)
- AC-S145 Startup: RecuperaElaborazioniInterrotte and RecuperaRiassuntiInterrotti both run before the first claim
- AC-S146 E2E (real SQLite file, fake ML + ModelloLinguisticoFinto bound for the test): Riassumi → in_attesa → the queue runs it → riassunto-vista shows the pronto; with an Elaborazione requested between two Riassunti the three run in request order
- AC-S147 E2E Elimina: a Registrazione with a pronto and an in_attesa Riassunto → EliminaRegistrazione → row counts of riassunto, riassunto_elemento, riassunto_fonte for it are 0; with AbbonatoProgettoSintesi NOT registered the delete fails on the FK and nothing changes
- AC-S148 E2E Ritrascrivi: a completata re-run over a Registrazione with a pronto → the old one is gone and ONE in_attesa with the same Argomento exists, visible only after the new Trascritto commits; a fallita re-run changes nothing
- AC-S149 E2E deletion during a run: ModelloLinguisticoFinto blocks on a latch, EliminaRegistrazione commits, the latch is released → no riassunto row, neither RiassuntoPronto nor RiassuntoFallito delivered, the queue moves on
- AC-S150 The LLM run takes no sherpa Mutex: a Conferma extraction completes while a fake Riassunto run is blocked (ADR 0023 §5)
- AC-S151 --smoke with a fixture holding a pronto Riassunto captures S3 with the Riassunto tab selected to avvio/build/smoke/ and exits 0
- AC-S161 (MANDATORY, user 2026-09-26, D-0004) A late or duplicate RiassuntoEliminato does not cancel a Riassunto that still exists: the RiassuntoEliminato(r) handler flips the running run's annullato only if the running Riassunto's own row is gone (riassunti.trova(runningId) == null); the flag is per run — keyed by the running RiassuntoId, a fresh one for each claim — never a service-lifetime latch. Test (real SQLite, ModelloLinguisticoFinto on a latch): sostituzione re-queues X for r (commits RiassuntoEliminato(r) + RiassuntoRichiesto(r)); X is claimed first; then the Eliminato is delivered → X completes normally (pronto, not stuck in_corso) and a later Riassumi on r is not refused; duplicate delivery: the same RiassuntoEliminato(r) delivered twice after X is claimed → X still completes, while AC-S149 (row really deleted) still cancels
- AC-S162 (user 2026-09-26, D-0006) Stop cancels the running Riassunto: ModelloLinguisticoFinto blocks on a latch and ignores thread interrupts, polling only annullato(); a Riassunto is claimed and running; CodaCondivisa.fermaEAttendi(timeout) → the Riassunto source's interrompi flips the current run's annullato though the riassunto row still exists → the fake returns Errore(Annullato), fermaEAttendi returns true within the timeout; the per-run flag of a later claim starts false

## Dependencies
- **vista-riassunto** (consumed; owner riassunto-vista; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoVista`: ≡ riassunto-vista.view_shape (one Published Language written once; rule 16) — RiassuntoVista.di(r): RiassuntoVista? via class RiassuntoVisteLettura (..letture)
  - key `voceId / segmentoId in the view`: Int values of the CURRENT Trascritto generation
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **coda-condivisa** (consumed; owner avvio-coda-condivisa; projection in-process; contract_test `consumer-driven`)
  - `FonteCoda (internal, snastro.avvio)`: class(tipo: TipoElementoCoda, teste: (esclusi: Set<String>) -> ElementoInCoda?, prossima: (esclusi: Set<String>, limite: Instant?) -> RisultatoTentativo, ultimaTentata: () -> String?, recupera: () -> Unit, trattenuta: () -> Boolean /* true = hold the whole queue while this source's head waits (sherpa models not pronti) */, annulla: (registrazioneId: String) -> Unit = {} /* D-0005: hook annullaInCorso(tipo, registrazioneId) delegates to for the item whose claim is in flight (AC-S63); Elaborazione source: no-op; avvio-sintesi's Riassunto source supplies it (AC-S161) */, interrompi: () -> Unit = {} /* D-0006: STOP channel — fermaEAttendi calls it on the source whose item is running, before/while interrupting the worker; the source flips its current run's annullato UNCONDITIONALLY (unlike annulla); Elaborazione source: no-op (thread interrupt suffices); avvio-sintesi's Riassunto source supplies it (AC-S162) */)
  - `ElementoInCoda`: data class(id: String, registrazioneId: String, istante: Instant)
  - `TipoElementoCoda`: enum { ELABORAZIONE /* 0 */, RIASSUNTO /* 1 */ } — ordinal is the tie-breaker
  - `CodaCondivisa`: class(scope, fonti: List<FonteCoda>, segnalaBloccato: (String) -> Unit, …) { fun avanza(); fun annullaInCorso(tipo: TipoElementoCoda, registrazioneId: String); fun fermaEAttendi(timeoutMs): Boolean; : PosizioniNellaCoda }
  - `RisultatoTentativo`: existing sealed { Nessuno; Avviata(id); Rifiutata(id) }
  - key `order`: (istante, tipo.ordinal, id) — total, deterministic; a single-thread dispatcher (AC-314) claims; the bound passed to a claim = the other source's head instant
  - delivery: in-process, single worker thread; signals coalesced (avanza) + 1 s re-arm; recupera() per source at start and after an escape
- **riassunti-in-coda** (consumed; owner riassunti-in-attesa; projection in-process; contract_test `consumer-driven`)
  - `RiassuntiInAttesa (snastro.sintesi.applicazione.letture)`: class { fun elenco(): List<RiassuntoInCoda> } — FIFO (richiestoAlle, id), in_attesa only
  - `RiassuntoInCoda`: data class(riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant)
  - `EseguiProssimoRiassunto`: data class(esclusi: Set<String> = emptySet(), primaDi: Instant? = null) — claims only if richiestoAlle < primaDi (strict: the Elaborazione wins an equal ms); RecuperaRiassuntiInterrotti()
  - key `riassuntoId`: see agg-riassunto (String value of RiassuntoId)
  - key `richiestoAlle`: see agg-riassunto
- **eventi-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRichiesto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoAvviato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoPronto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoFallito`: data class(registrazioneId: RegistrazioneId, motivo: String /* MotivoFallimento canonical code */) : EventoPubblicato
  - `RiassuntoEliminato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `LunghezzaMassimaRiassuntoModificata`: data class(progettoId: ProgettoId) : EventoPubblicato
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, background coroutine, coalesced per registrazioneId; NO synchronous subscriber; consumers (all in :avvio) idempotent; single writer per key (one process, one DB)
- **posizioni-nella-coda** (consumed; owner posizioni-nella-coda; projection in-process; contract_test `consumer-driven`)
  - `PosizioniNellaCoda (snastro.ui.coda)`: interface { fun istantanea(): PosizioniCoda }
  - `PosizioniCoda`: data class(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>) { companion VUOTA } — 1-based over ALL in_attesa items of both kinds in the global order; in_corso not counted
  - key `RegistrazioneId`: exact per kind: at most one open item per Registrazione per kind (INV-4, INV-S2)
- **ui-schede-registrazione** (consumed; owner schede-registrazione; projection in-process; contract_test `consumer-driven`)
  - `SorgenteRiassuntoS3 (optional presenter input)`: data class(contenuto: @Composable (RegistrazioneId) -> Unit, segno: (RegistrazioneId) -> Flow<SegnoScheda?>) — null in R1/R2 (no tabs)
- **tec-shell-ui** (REUSED — boundary `tec-shell-ui` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `ui-fondamenta` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: AggiornamentiVista.cambiamenti: Flow<Cambiamento>; Cambiamento(registrazioneId: RegistrazioneId?) — as pinned in the sibling manifest
- **disponibilita-modello** (consumed; owner disponibilita-modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `DisponibilitaModelloLinguistico`: interface { fun stato(): StatoModelloLinguistico }
  - `StatoModelloLinguistico`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }
  - `MotivoDownload`: enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }
- **tec-modello-linguistico** (consumed; owner modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `ModelloLinguistico`: interface { fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> } — blocking; NEVER called inside a UnitaDiLavoro transaction
  - `RichiestaRiassunto`: data class(ingresso: String, argomento: String?, lunghezzaMassimaParole: Int)
  - `RispostaModello`: data class(sommario: String?, decisioni: List<ElementoRisposta>, questioniAperte: List<ElementoRisposta>, azioni: List<AzioneRisposta>, puntiChiave: List<PuntoChiaveRisposta>) — raw, UNVERIFIED; speakers only as {V<n>}
  - `ElementoRisposta`: data class(testo: String, fonti: List<Int>)
  - `AzioneRisposta`: data class(testo: String, fonti: List<Int>, responsabile: Int?)
  - `PuntoChiaveRisposta`: data class(testo: String, fonti: List<Int>, parlante: Int?)
  - `ErroreApplicazioneSintesi`: sealed : ErroreDominio { ModelloNonDisponibile; IngressoTroppoLungo(token: Int); ErroreRuntime(motivo: String); RispostaNonValida; Annullato } → motivo: modello_non_disponibile, troppo_lunga, errore_modello, errore_modello, (nothing written)
  - key `fonti / responsabile / parlante`: segmentoId / voceId NUMBERS of the Trascritto generation the input was built from (Published Language integers); validity is NOT the port's promise — the root checks it (INV-S4)
- avvio-coda-condivisa — build dependency (merged before this block)
- modello-facoltativo-avvio — build dependency (merged before this block)
- esegui-riassunto — build dependency (merged before this block)
- riassunti-in-attesa — build dependency (merged before this block)
- abbonato-trascrizione-sintesi — build dependency (merged before this block)
- abbonato-progetto-sintesi — build dependency (merged before this block)
- lettore-trascritto-da-trascrizione-sintesi — build dependency (merged before this block)
- lettore-nomi-da-parlanti-sintesi — build dependency (merged before this block)
- repository-sql-sintesi — build dependency (merged before this block)
- schede-registrazione — build dependency (merged before this block)
- scheda-riassunto — build dependency (merged before this block)
- dialogo-elimina-riassunto — build dependency (merged before this block)

Sources: ADR 0021 §10, ADR 0023 §2/§5, ADR 0024 §1/§4, ADR 0025 §4, profile run binding; related_adrs 0002, 0004, 0010, 0012, 0020, 0021, 0023, 0024, 0025; tactical-model: features/sintesi/tactical-model.md
