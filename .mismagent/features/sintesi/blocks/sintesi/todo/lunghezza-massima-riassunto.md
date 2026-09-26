---
id: "lunghezza-massima-riassunto"
type: "aggregate"
context: "sintesi"
side: "app"
wave: 2
release: "R3"
module: ":sintesi:dominio"
consumes:
  - "agg-riassunto"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0021"
  - "0022"
tests_nl_status: "confirmed"
invariants:
  - "INV-S9 the lunghezza massima del Riassunto is an integer number of words within [300, 2500], default 2000; out of range → LunghezzaMassimaFuoriIntervallo, nothing changed"
invariant_fields:
  - "parole"
tables:
  - "impostazioni_sintesi"
identity: "progettoId (ProgettoId, kernel) — one per Progetto"
---
# lunghezza-massima-riassunto — Aggregato LunghezzaMassimaRiassunto (impostazione per Progetto) + VO LunghezzaMassimaParole

## What to do
The per-Progetto Sintesi setting: root LunghezzaMassimaRiassunto (identity progettoId) holding the VO LunghezzaMassimaParole (words, [300, 2500], default 2000, provisional bounds with one home). No row ⇒ the default. Never deleted. Uses the LunghezzaMassimaFuoriIntervallo variant already declared by the riassunto block.

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S9 the lunghezza massima del Riassunto is an integer number of words within [300, 2500], default 2000; out of range → LunghezzaMassimaFuoriIntervallo, nothing changed

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- INV-S9 LunghezzaMassimaParole.di: 300, 2000 and 2500 → Ok; 299, 2501, 0 and -1 → Errore(LunghezzaMassimaFuoriIntervallo(valore, 300, 2500)) (table test); the constants MINIMO 300 / MASSIMO 2500 / PREDEFINITA 2000 exist only in this VO
- AC-S48 LunghezzaMassimaRiassunto.predefinita(progettoId) holds 2000 — the value read for a Progetto created before this feature (no row)
- AC-S49 modifica(1500) → the root holds 1500 and returns LunghezzaMassimaRiassuntoModificata(progettoId); modifica(2600) → Errore(LunghezzaMassimaFuoriIntervallo), value unchanged, no event

## Dependencies
- **agg-lunghezza-massima-riassunto** (OWNED here; owner lunghezza-massima-riassunto; projection in-process; contract_test `invariant-test`)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 (provisional, spikes runtime-llm-in-app / qualita-riassunto)
  - `LunghezzaMassimaRiassunto`: root(progettoId: ProgettoId, parole: LunghezzaMassimaParole); LunghezzaMassimaRiassunto.predefinita(progettoId); modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio>
  - key `progettoId`: ProgettoId (kernel) minted by crea-progetto — one setting per Progetto; no row ⇒ PREDEFINITA
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
  - `ErroreSintesi (ErroriSintesi.kt, : ErroreDominio)`: RiassuntoGiaAperto(registrazioneId); ModelloNonInstallato; TrascrittoNonDisponibile(registrazioneId); ElaborazioneGiaAperta(registrazioneId); RegistrazioneTroppoLunga(stimaToken: Int, limite: Int); ArgomentoTroppoLungo(lunghezza: Int, massimo: Int); LunghezzaMassimaFuoriIntervallo(valore: Int, minimo: Int, massimo: Int); TransizioneNonAmmessa(da: String, verso: String); RiassuntoNonTrovato(id: String)
  - key `RiassuntoId`: minted by riassumi and by sostituzione-trascritto-sintesi-policy via GeneratoreId (UUID v4) — never reused, stable for the row's life; crosses to :avvio only as its String value (ElementoInCoda.id, esclusi)
  - key `richiestoAlle`: minted by the requesting command from the injected Clock, stored as epoch millis — the FIFO key of the shared queue; orderable (Instant at ms precision), ties broken by (tipo, id) (rule 17)
  - key `struttura`: minted by StrutturaTrascritto.chiave from the Segmenti read in the run — canonical, collision-free, stable across renames/Attribuzioni (only a Revisione changes it)

Sources: tactical-model § Lunghezza massima del Riassunto, INV-S9; ADR 0022 §2; related_adrs 0002, 0003, 0007, 0012, 0021, 0022; tactical-model: features/sintesi/tactical-model.md
