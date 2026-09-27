---
id: "porte-sintesi"
type: "port"
context: "sintesi"
side: "app"
wave: 3
release: "R3"
module: ":sintesi:applicazione (..porte, ..eventi) + testFixtures"
consumes:
  - "agg-riassunto"
  - "agg-lunghezza-massima-riassunto"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0007"
  - "0012"
  - "0021"
  - "0022"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  RiassuntoRepository: "interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; riassunto_non_pronto_unico OR riassunto_pronto_unico violation → Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId)), nothing written; any other constraint failure → infra fault (ADR 0003) — D-0003 */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS in ONE call (ADR 0022 §4 steps 1–3): re-read the row; absent or not in_corso → Ok(false), nothing written; only when r is pronto, concludi ITSELF removes the previous pronto of the same Registrazione (after the in_corso check, same call — callers never remove it first); then UPDATE … WHERE id AND stato='in_corso' + children → Ok(true) — D-0003 */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }"
  LunghezzaMassimaRiassuntoRepository: "interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }"
  RiassuntoRichiesto: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato"
  RiassuntoAvviato: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato"
  RiassuntoPronto: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato"
  RiassuntoFallito: "data class(registrazioneId: RegistrazioneId, motivo: String /* MotivoFallimento canonical code */) : EventoPubblicato"
  RiassuntoEliminato: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato"
  LunghezzaMassimaRiassuntoModificata: "data class(progettoId: ProgettoId) : EventoPubblicato"
contract_test: "consumer-driven"
---
# porte-sintesi — Porte di persistenza Sintesi (RiassuntoRepository, LunghezzaMassimaRiassuntoRepository) + eventi pubblicati

## What to do
Declare RiassuntoRepository (incl. the completion compare-and-set concludi) and LunghezzaMassimaRiassuntoRepository with their abstract contracts and Finte (the fake honours both partial indexes, like the other repository fakes), and the six published events in snastro.sintesi.applicazione.eventi.

## Tasks
- AC-S65 RiassuntoRepositoryContratto (vs Finta): round-trip of a pronto with all four element types, Fonte sets, {V<n>} tokens and literal braces, struttura, omessi, cap and argomento (null and non-null); round-trip of in_attesa, in_corso and fallito(motivo)
- AC-S66 RiassuntoRepositoryContratto: salva of a second in_attesa/in_corso/fallito for the same Registrazione → Errore(RiassuntoGiaAperto(registrazioneId)); a second pronto → Errore(RiassuntoGiaAperto(registrazioneId)) too (backstop, D-0003); nothing written in both cases
- AC-S67 RiassuntoRepositoryContratto: inAttesa() is FIFO by (richiestoAlle, id); inCorso() lists in_corso only; diRegistrazione(r) lists every state of r only
- AC-S68 RiassuntoRepositoryContratto concludi(r) (CAS): row absent → Ok(false), nothing written; row in_attesa / pronto / fallito → Ok(false), row unchanged; row in_corso → Ok(true), the new state and children written
- AC-S69 RiassuntoRepositoryContratto: rimuoviDiRegistrazione(r) returns the number removed (0 on none) and removes elements and Fonti; rimuovi(id) of an absent id is a no-op
- AC-S70 LunghezzaMassimaRiassuntoRepositoryContratto: trova(p) with no row → 2000; salva(1500) then trova(p) → 1500; another Progetto unaffected
- AC-S71 Published events are data classes implementing EventoPubblicato with exactly the pinned fields (eventi-sintesi)

## Dependencies
- **repo-sintesi** (OWNED here; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; riassunto_non_pronto_unico OR riassunto_pronto_unico violation → Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId)), nothing written; any other constraint failure → infra fault (ADR 0003) — D-0003 */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS in ONE call (ADR 0022 §4 steps 1–3): re-read the row; absent or not in_corso → Ok(false), nothing written; only when r is pronto, concludi ITSELF removes the previous pronto of the same Registrazione (after the in_corso check, same call — callers never remove it first); then UPDATE … WHERE id AND stato='in_corso' + children → Ok(true) — D-0003 */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **eventi-sintesi** (OWNED here; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRichiesto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoAvviato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoPronto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoFallito`: data class(registrazioneId: RegistrazioneId, motivo: String /* MotivoFallimento canonical code */) : EventoPubblicato
  - `RiassuntoEliminato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `LunghezzaMassimaRiassuntoModificata`: data class(progettoId: ProgettoId) : EventoPubblicato
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, background coroutine, coalesced per registrazioneId; NO synchronous subscriber; consumers (all in :avvio) idempotent; single writer per key (one process, one DB)
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
- **agg-lunghezza-massima-riassunto** (consumed; owner lunghezza-massima-riassunto; projection in-process; contract_test `invariant-test`)
  - `LunghezzaMassimaRiassunto`: root(progettoId: ProgettoId, parole: LunghezzaMassimaParole /* pinned in agg-riassunto */); LunghezzaMassimaRiassunto.predefinita(progettoId); modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio>
  - key `progettoId`: ProgettoId (kernel) minted by crea-progetto — one setting per Progetto; no row ⇒ PREDEFINITA

Sources: ADR 0022 §4, ADR 0021 §3 (events); related_adrs 0002, 0003, 0007, 0012, 0021, 0022; tactical-model: features/sintesi/tactical-model.md
