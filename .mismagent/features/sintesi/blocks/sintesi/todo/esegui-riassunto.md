---
id: "esegui-riassunto"
type: "application-service"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..comandi)"
consumes:
  - "agg-riassunto"
  - "repo-sintesi"
  - "trascritto-per-sintesi"
  - "nomi-per-sintesi"
  - "tec-modello-linguistico"
  - "disponibilita-modello"
  - "eventi-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0003"
  - "0012"
  - "0021"
  - "0022"
  - "0023"
model_hint: "deep"
tests_nl_status: "confirmed"
invariants:
  - "INV-S3 a transition to pronto deletes the previous pronto in the same transaction; a fallito leaves the shown pronto byte-for-byte unchanged"
  - "INV-S4 applied by the root on the raw answer against the structure read in THIS run"
  - "INV-S8 completion is a compare-and-set on the row still existing and still in_corso; otherwise nothing is written and nothing published"
  - "INV-S10 the Riassunto's own cap is passed to the model"
commands:
  - "EseguiProssimoRiassunto(esclusi: Set<String>, primaDi: Instant?)"
  - "RecuperaRiassuntiInterrotti"
---
# esegui-riassunto — EseguiProssimoRiassunto + RecuperaRiassuntiInterrotti

## What to do
Three phases, no transaction around the LLM: (1) claim — inside a short BEGIN IMMEDIATE transaction read the oldest eligible in_attesa (not in esclusi, richiestoAlle < primaDi), move it to in_corso, publish RiassuntoAvviato; (2) outside any transaction read the Segmenti and current names, build IngressoRiassunto, call ModelloLinguistico with the Riassunto's cap and Argomento and the annullato flag, apply the Verifica on the root; (3) the ADR 0022 §4 compare-and-set commits pronto (replacing) or fallito, or nothing. RecuperaRiassuntiInterrotti marks in_corso rows with no live run fallito interrotto.

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S3 a transition to pronto deletes the previous pronto in the same transaction; a fallito leaves the shown pronto byte-for-byte unchanged
- INV-S4 applied by the root on the raw answer against the structure read in THIS run
- INV-S8 completion is a compare-and-set on the row still existing and still in_corso; otherwise nothing is written and nothing published
- INV-S10 the Riassunto's own cap is passed to the model

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S83 EseguiProssimoRiassunto(esclusi: Set<String>, primaDi: Instant?) claim: two in_attesa (t1 < t2) → the t1 one becomes in_corso with avviatoAlle = clock and RiassuntoAvviato is published; with primaDi = t1 → Nessuno, nothing written; with t1's id in esclusi → t2 is claimed; empty queue → Nessuno
- AC-S84 The model and both lettori are invoked OUTSIDE any transaction: every test runs with ModelloLinguisticoFinto's transaction guard, which throws if a regression moves the call inside
- AC-S85 Input: the RichiestaRiassunto carries the IngressoRiassunto built from LettoreTrascritto's Segmenti and LettoreNomi's legend (named Voce → Nome, unattributed → 'Voce n'), the Riassunto's Argomento, and ITS cap — requested at 1500, Progetto changed to 2500 before the run → 1500 is sent
- AC-S86 Success: the verified content is committed pronto, the previous pronto of that Registrazione is removed in the same transaction, RiassuntoPronto is published; afterwards exactly one pronto exists
- AC-S87 Failure mapping, each leaving the shown pronto byte-identical and publishing RiassuntoFallito(motivo): ModelloNonDisponibile or DisponibilitaModelloLinguistico not Installato at run time → modello_non_disponibile (model not called in the second case); IngressoTroppoLungo → troppo_lunga; ErroreRuntime / RispostaNonValida → errore_modello; everything dropped by the Verifica → nessun_contenuto_verificabile
- INV-S8 race: the Riassunto is removed (eliminazione or sostituzione policy) while the model runs → the completion writes no row, resurrects nothing, publishes neither RiassuntoPronto nor RiassuntoFallito; the model answering Errore(Annullato) → nothing written
- AC-S88 A Revisione committed while the run is in_corso: the Verifica uses the structure read in the run, the stored struttura is that one, so the new Riassunto is born superato (asserted through the root predicate on the current structure)
- AC-S89 RecuperaRiassuntiInterrotti: every in_corso row → fallito interrotto + RiassuntoFallito; in_attesa rows untouched; running it twice changes nothing more

## Dependencies
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **agg-riassunto** (consumed; owner riassunto; projection in-process; contract_test `invariant-test`)
  - `Riassunto.richiedi`: (id: RiassuntoId, registrazioneId: RegistrazioneId, argomento: Argomento?, lunghezzaMassima: LunghezzaMassimaParole, richiestoAlle: Instant): Creato<Riassunto, RiassuntoRichiestoDominio> — in_attesa
  - `Riassunto.avvia`: (alle: Instant): Esito<RiassuntoAvviatoDominio> — in_attesa → in_corso
  - `Riassunto.completa`: (bozza: BozzaRiassunto, struttura: StrutturaTrascritto): Esito<ConclusioneRiassunto> — in_corso → pronto (Verifica delle fonti applied, INV-S4) | fallito(NESSUN_CONTENUTO_VERIFICABILE)
  - `Riassunto.fallisci`: (motivo: MotivoFallimento): Esito<RiassuntoFallitoDominio> — in_corso → fallito
  - `ConclusioneRiassunto`: sealed { Pronto(omessi: Int); Fallito(motivo: MotivoFallimento) }
  - `named predicates`: aperto (in_attesa|in_corso), inAttesa, inCorso, pronto, fallito, superato(corrente: StrutturaTrascritto): Boolean (false unless pronto) — never compare StatoRiassunto outside the aggregate
  - `BozzaRiassunto`: data class(sommario: String?, decisioni: List<BozzaElemento>, questioniAperte: List<BozzaElemento>, azioni: List<BozzaElemento>, puntiChiave: List<BozzaElemento>) — raw, unverified; texts in the {V<n>} form
  - `BozzaElemento`: data class(testo: String, fonti: List<Int> /* segmentoId numbers */, voce: Int? /* Responsabile for azioni, speaker for puntiChiave, must be null otherwise */)
  - `StrutturaTrascritto`: StrutturaTrascritto.di(coppie: List<Pair<SegmentoId, VoceId>>); val chiave: String ('<segmentoId>:<voceId>' ordered by segmentoId, joined by ','); contiene(segmentoId); voceDi(segmentoId): VoceId?; voci: Set<VoceId>
  - `TestoConVoci`: data class(parti: List<ParteTesto>); ParteTesto = Testo(String) | Voce(VoceId); codifica(): String ({V<n>}, literal braces doubled); TestoConVoci.decodifica(s: String): TestoConVoci? (null ⇔ some token is malformed: a lone '{' or '}', '{V}', '{V0}', '{Vx}'; not an ErroreSintesi — the only expected source is the model's answer, consumed inside the root by the Verifica delle fonti; a reader of a STORED text, written only by codifica, treats null as a fault: checkNotNull) — D-0002
  - `Elements (read side)`: Decisione / QuestioneAperta(testo: TestoConVoci, fonti: Set<SegmentoId>); Azione(…, responsabile: VoceId?); PuntoChiave(…, parlante: VoceId?); Sommario(testo: TestoConVoci)
  - `Argomento`: Argomento.di(testo: String?): Esito<Argomento?> — trimmed, blank → null, > MASSIMO_CARATTERI (200, provisional) → ArgomentoTroppoLungo
  - `MotivoFallimento`: enum { MODELLO_NON_DISPONIBILE('modello_non_disponibile'), ERRORE_MODELLO('errore_modello'), TROPPO_LUNGA('troppo_lunga'), NESSUN_CONTENUTO_VERIFICABILE('nessun_contenuto_verificabile'), INTERROTTO('interrotto') } — canonical codes stored in motivo_fallimento
  - `Riassumibilita`: object { fun valuta(registrazioneId: RegistrazioneId, modelloInstallato: Boolean, trascrittoPresente: Boolean, elaborazioneAperta: Boolean, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> } (registrazioneId only feeds the errors that carry it — D-0002) — errors in this order: ModelloNonInstallato, TrascrittoNonDisponibile, ElaborazioneGiaAperta, RiassuntoGiaAperto, RegistrazioneTroppoLunga
  - `IngressoRiassunto / LimiteIngresso`: IngressoRiassunto.costruisci(segmenti: List<SegmentoIngresso>, nomi: Map<VoceId, String>): String (lines '[s<segmentoId> V<voceId>] <testo>', no m:ss, + legend — ADR 0021 amendment 2026-09-26); SegmentoIngresso(segmentoId: SegmentoId, voceId: VoceId, inizioMs: Long, testo: String) (inizioMs kept in the shape, unused by the line for now); LimiteIngresso.stimaToken(ingresso: String): Int = ceil(5*chars/12) (≡ ceil(chars/2.4), integer arithmetic); LimiteIngresso.LIMITE_TOKEN = 28_000 (calibrated, ADR 0026 §5)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 — owned by riassunto since D-0001 (MASSIMO 2500 confirmed by ADR 0026 §5; still refinable by spike qualita-riassunto)
  - `ErroreSintesi (ErroriSintesi.kt, : ErroreDominio)`: RiassuntoGiaAperto(registrazioneId); ModelloNonInstallato; TrascrittoNonDisponibile(registrazioneId); ElaborazioneGiaAperta(registrazioneId); RegistrazioneTroppoLunga(stimaToken: Int, limite: Int); ArgomentoTroppoLungo(lunghezza: Int, massimo: Int); LunghezzaMassimaFuoriIntervallo(valore: Int, minimo: Int, massimo: Int); TransizioneNonAmmessa(da: String, verso: String); RiassuntoNonTrovato(id: String) — closed list: no variant for a malformed {V<n>} token (TestoConVoci.decodifica returns null, D-0002)
  - key `RiassuntoId`: minted by riassumi and by sostituzione-trascritto-sintesi-policy via GeneratoreId (UUID v4) — never reused, stable for the row's life; crosses to :avvio only as its String value (ElementoInCoda.id, esclusi)
  - key `richiestoAlle`: minted by the requesting command from the injected Clock, stored as epoch millis — the FIFO key of the shared queue; orderable (Instant at ms precision), ties broken by (tipo, id) (rule 17)
  - key `struttura`: minted by StrutturaTrascritto.chiave from the Segmenti read in the run — canonical, collision-free, stable across renames/Attribuzioni (only a Revisione changes it)
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **trascritto-per-sintesi** (consumed; owner lettore-trascritto-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreTrascritto (snastro.sintesi.applicazione.porte)`: interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }
  - `SegmentoSintesi`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione
  - key `SegmentoId / VoceId`: see kernel-pl — per Trascritto generation
- **nomi-per-sintesi** (consumed; owner lettore-nomi-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreNomi (snastro.sintesi.applicazione.porte)`: interface { fun nomi(r: RegistrazioneId): Map<VoceRef, String> } — attributed Voci only, current Nome, an eliminato still resolves; read at run time and display, NEVER stored (INV-S5)
  - key `VoceRef`: kernel composite (registrazioneId, voceId) — see kernel-pl
- **tec-modello-linguistico** (consumed; owner modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `ModelloLinguistico`: interface { fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> } — blocking; NEVER called inside a UnitaDiLavoro transaction
  - `RichiestaRiassunto`: data class(ingresso: String, argomento: String?, lunghezzaMassimaParole: Int)
  - `RispostaModello`: data class(sommario: String?, decisioni: List<ElementoRisposta>, questioniAperte: List<ElementoRisposta>, azioni: List<AzioneRisposta>, puntiChiave: List<PuntoChiaveRisposta>) — raw, UNVERIFIED; speakers only as {V<n>}
  - `ElementoRisposta`: data class(testo: String, fonti: List<Int>)
  - `AzioneRisposta`: data class(testo: String, fonti: List<Int>, responsabile: Int?)
  - `PuntoChiaveRisposta`: data class(testo: String, fonti: List<Int>, parlante: Int?)
  - `ErroreApplicazioneSintesi`: sealed : ErroreDominio { ModelloNonDisponibile; IngressoTroppoLungo(token: Int); ErroreRuntime(motivo: String); RispostaNonValida; Annullato } → motivo: modello_non_disponibile, troppo_lunga, errore_modello, errore_modello, (nothing written)
  - key `fonti / responsabile / parlante`: segmentoId / voceId NUMBERS of the Trascritto generation the input was built from (Published Language integers); validity is NOT the port's promise — the root checks it (INV-S4)
- **disponibilita-modello** (consumed; owner disponibilita-modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `DisponibilitaModelloLinguistico`: interface { fun stato(): StatoModelloLinguistico }
  - `StatoModelloLinguistico`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }
  - `MotivoDownload`: enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }
- **eventi-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRichiesto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoAvviato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoPronto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoFallito`: data class(registrazioneId: RegistrazioneId, motivo: String /* MotivoFallimento canonical code */) : EventoPubblicato
  - `RiassuntoEliminato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `LunghezzaMassimaRiassuntoModificata`: data class(progettoId: ProgettoId) : EventoPubblicato
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, background coroutine, coalesced per registrazioneId; NO synchronous subscriber; consumers (all in :avvio) idempotent; single writer per key (one process, one DB)
- **riassunti-in-coda** (SUPPLIED here; owner riassunti-in-attesa; projection in-process; contract_test `consumer-driven`)
  - `RiassuntiInAttesa (snastro.sintesi.applicazione.letture)`: class { fun elenco(): List<RiassuntoInCoda> } — FIFO (richiestoAlle, id), in_attesa only
  - `RiassuntoInCoda`: data class(riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant)
  - `EseguiProssimoRiassunto`: data class(esclusi: Set<String> = emptySet(), primaDi: Instant? = null) — claims only if richiestoAlle < primaDi (strict: the Elaborazione wins an equal ms); RecuperaRiassuntiInterrotti()
  - key `riassuntoId`: see agg-riassunto (String value of RiassuntoId)
  - key `richiestoAlle`: see agg-riassunto

Sources: tactical-model § Commands (EseguiProssimoRiassunto, RecuperaRiassuntiInterrotti), INV-S3/S4/S8/S10; ADR 0023 §2–3 & §5; ADR 0022 §4; ADR 0021 §4; related_adrs 0002, 0003, 0007, 0012, 0018, 0021, 0022, 0023, 0025; tactical-model: features/sintesi/tactical-model.md
