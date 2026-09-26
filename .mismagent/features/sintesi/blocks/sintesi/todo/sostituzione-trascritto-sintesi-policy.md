---
id: "sostituzione-trascritto-sintesi-policy"
type: "application-service"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..politiche)"
consumes:
  - "agg-riassunto"
  - "agg-lunghezza-massima-riassunto"
  - "repo-sintesi"
  - "trascritto-per-sintesi"
  - "disponibilita-modello"
  - "eventi-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0012"
  - "0018"
  - "0021"
model_hint: "deep"
tests_nl_status: "confirmed"
invariants:
  - "INV-S8 no Riassunto outlives its Trascritto generation: every Riassunto of r removed in the publishing transaction"
  - "INV-S10 the automatic re-summary carries the Progetto's CURRENT cap"
commands:
  - "ApplicaSostituzioneTrascrittoSintesi(registrazioneId)"
---
# sostituzione-trascritto-sintesi-policy — Policy su TrascrittoSostituito: elimina i Riassunti e riaccoda un Riassumi automatico

## What to do
Synchronous, inside the re-run's completion transaction (after ADR 0018 §2 saved the new Trascritto): remove every Riassunto of r (any state); if at least one existed and the NEW Trascritto passes Riassumibilita restricted to {model Installato, input within the limit}, create one in_attesa with the Argomento of the most recent removed one (by richiestoAlle), the Progetto's current cap, richiestoAlle = now; publish RiassuntoEliminato (+ RiassuntoRichiesto). Structural: never calls ModelloLinguistico.

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S8 no Riassunto outlives its Trascritto generation: every Riassunto of r removed in the publishing transaction
- INV-S10 the automatic re-summary carries the Progetto's CURRENT cap

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S92 ApplicaSostituzioneTrascrittoSintesi(registrazioneId): r has a pronto (argomento 'A1', t1) and a fallito (argomento 'A2', t2 > t1) → both removed with their elements and Fonti; ONE new in_attesa with argomento 'A2', the Progetto's current cap and richiestoAlle = clock; RiassuntoEliminato(r) and RiassuntoRichiesto(r) published
- AC-S93 Any state is removed, in_attesa and in_corso included (the in_corso run's completion will then write nothing — esegui-riassunto INV-S8)
- AC-S94 r has no Riassunto → Ok, nothing created, nothing published (the only automatic Riassumi exists only when one existed)
- AC-S95 New Trascritto over the input limit, or model not Installato → the old ones are removed, NOTHING is created, only RiassuntoEliminato is published; the policy returns Ok (never Errore for a refused re-summary)
- AC-S96 The limit is evaluated on the NEW Trascritto read through LettoreTrascritto inside the same transaction (fake returns the new generation)
- AC-S97 A repository Errore is returned unchanged (the completion transaction rolls back; ADR 0018 §6 compensates); ModelloLinguistico is not a collaborator (constructor test)

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
  - `TestoConVoci`: data class(parti: List<ParteTesto>); ParteTesto = Testo(String) | Voce(VoceId); codifica(): String ({V<n>}, literal braces doubled); TestoConVoci.decodifica(s: String): Esito<TestoConVoci> (malformed token → Errore)
  - `Elements (read side)`: Decisione / QuestioneAperta(testo: TestoConVoci, fonti: Set<SegmentoId>); Azione(…, responsabile: VoceId?); PuntoChiave(…, parlante: VoceId?); Sommario(testo: TestoConVoci)
  - `Argomento`: Argomento.di(testo: String?): Esito<Argomento?> — trimmed, blank → null, > MASSIMO_CARATTERI (200, provisional) → ArgomentoTroppoLungo
  - `MotivoFallimento`: enum { MODELLO_NON_DISPONIBILE('modello_non_disponibile'), ERRORE_MODELLO('errore_modello'), TROPPO_LUNGA('troppo_lunga'), NESSUN_CONTENUTO_VERIFICABILE('nessun_contenuto_verificabile'), INTERROTTO('interrotto') } — canonical codes stored in motivo_fallimento
  - `Riassumibilita`: object { fun valuta(modelloInstallato: Boolean, trascrittoPresente: Boolean, elaborazioneAperta: Boolean, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> } — errors in this order: ModelloNonInstallato, TrascrittoNonDisponibile, ElaborazioneGiaAperta, RiassuntoGiaAperto, RegistrazioneTroppoLunga
  - `IngressoRiassunto / LimiteIngresso`: IngressoRiassunto.costruisci(segmenti: List<SegmentoIngresso>, nomi: Map<VoceId, String>): String; SegmentoIngresso(segmentoId: SegmentoId, voceId: VoceId, inizioMs: Long, testo: String); LimiteIngresso.stimaToken(ingresso: String): Int = ceil(chars/3); LimiteIngresso.LIMITE_TOKEN = 28_000 (provisional)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 — owned by riassunto since D-0001 (provisional, spikes runtime-llm-in-app / qualita-riassunto)
  - `ErroreSintesi (ErroriSintesi.kt, : ErroreDominio)`: RiassuntoGiaAperto(registrazioneId); ModelloNonInstallato; TrascrittoNonDisponibile(registrazioneId); ElaborazioneGiaAperta(registrazioneId); RegistrazioneTroppoLunga(stimaToken: Int, limite: Int); ArgomentoTroppoLungo(lunghezza: Int, massimo: Int); LunghezzaMassimaFuoriIntervallo(valore: Int, minimo: Int, massimo: Int); TransizioneNonAmmessa(da: String, verso: String); RiassuntoNonTrovato(id: String)
  - key `RiassuntoId`: minted by riassumi and by sostituzione-trascritto-sintesi-policy via GeneratoreId (UUID v4) — never reused, stable for the row's life; crosses to :avvio only as its String value (ElementoInCoda.id, esclusi)
  - key `richiestoAlle`: minted by the requesting command from the injected Clock, stored as epoch millis — the FIFO key of the shared queue; orderable (Instant at ms precision), ties broken by (tipo, id) (rule 17)
  - key `struttura`: minted by StrutturaTrascritto.chiave from the Segmenti read in the run — canonical, collision-free, stable across renames/Attribuzioni (only a Revisione changes it)
- **agg-lunghezza-massima-riassunto** (consumed; owner lunghezza-massima-riassunto; projection in-process; contract_test `invariant-test`)
  - `LunghezzaMassimaRiassunto`: root(progettoId: ProgettoId, parole: LunghezzaMassimaParole /* pinned in agg-riassunto */); LunghezzaMassimaRiassunto.predefinita(progettoId); modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio>
  - key `progettoId`: ProgettoId (kernel) minted by crea-progetto — one setting per Progetto; no row ⇒ PREDEFINITA
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **trascritto-per-sintesi** (consumed; owner lettore-trascritto-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreTrascritto (snastro.sintesi.applicazione.porte)`: interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }
  - `SegmentoSintesi`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione
  - key `SegmentoId / VoceId`: see kernel-pl — per Trascritto generation
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

Sources: tactical-model § Policies (TrascrittoSostituito), INV-S8/S10; ADR 0021 §6; ADR 0018 §5; related_adrs 0002, 0003, 0007, 0012, 0018, 0021, 0022, 0025; tactical-model: features/sintesi/tactical-model.md
