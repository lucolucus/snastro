---
id: "modello-linguistico"
type: "port"
context: "sintesi"
side: "app"
wave: 1
release: "R3"
module: ":sintesi:applicazione (..porte) + testFixtures"
consumes: []
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0012"
  - "0021"
  - "0023"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  ModelloLinguistico: "interface { fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> } — blocking; NEVER called inside a UnitaDiLavoro transaction"
  RichiestaRiassunto: "data class(ingresso: String, argomento: String?, lunghezzaMassimaParole: Int)"
  RispostaModello: "data class(sommario: String?, decisioni: List<ElementoRisposta>, questioniAperte: List<ElementoRisposta>, azioni: List<AzioneRisposta>, puntiChiave: List<PuntoChiaveRisposta>) — raw, UNVERIFIED; speakers only as {V<n>}"
  ElementoRisposta: "data class(testo: String, fonti: List<Int>)"
  AzioneRisposta: "data class(testo: String, fonti: List<Int>, responsabile: Int?)"
  PuntoChiaveRisposta: "data class(testo: String, fonti: List<Int>, parlante: Int?)"
  ErroreApplicazioneSintesi: "sealed : ErroreDominio { ModelloNonDisponibile; IngressoTroppoLungo(token: Int); ErroreRuntime(motivo: String); RispostaNonValida; Annullato } → motivo: modello_non_disponibile, troppo_lunga, errore_modello, errore_modello, (nothing written)"
contract_test: "consumer-driven"
---
# modello-linguistico — Porta ModelloLinguistico (runtime-neutrale) + ErroreApplicazioneSintesi + Finta con guardia di transazione

## What to do
Declare the technical port ModelloLinguistico.riassumi(RichiestaRiassunto, annullato): Esito<RispostaModello> with its raw, UNVERIFIED answer types and ErroreApplicazioneSintesi; the abstract ModelloLinguisticoContratto; ModelloLinguisticoFinto (scripted answers, may return invalid Fonti on purpose, records the last RichiestaRiassunto, throws if invoked while UnitaDiLavoroFinta.transazioneAperta).

## Tasks
- AC-S11 ModelloLinguisticoContratto: the answer is structurally complete — sommario may be null, the four lists are present (possibly empty)
- AC-S12 ModelloLinguisticoContratto: no answer text contains a speaker in any form other than {V<n>} (regex on every text field: no 'V<n>' without braces, no '[V<n>]', no 'Voce <n>')
- AC-S13 ModelloLinguisticoContratto: once annullato() returns true (or the thread is interrupted) the call returns Errore(Annullato) within the bound passed to the contract (the fake: immediately; the real adapter: the spike's bound)
- AC-S14 ModelloLinguisticoFinto throws IllegalStateException when invoked while the fake UnitaDiLavoro has a transaction open (test: invoke inside inTransazione → throws; outside → answers) — the ADR 0012 (b) guard every esegui-riassunto test relies on
- AC-S15 ModelloLinguisticoFinto records the last RichiestaRiassunto (ingresso, argomento, lunghezzaMassimaParole) so callers can assert what was sent; it can be scripted to return each ErroreApplicazioneSintesi variant
- AC-S16 (by-construction) the contract never asserts Fonte validity — the root's job (INV-S4); not counted as coverage

## Dependencies
- **tec-modello-linguistico** (OWNED here; owner modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `ModelloLinguistico`: interface { fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> } — blocking; NEVER called inside a UnitaDiLavoro transaction
  - `RichiestaRiassunto`: data class(ingresso: String, argomento: String?, lunghezzaMassimaParole: Int)
  - `RispostaModello`: data class(sommario: String?, decisioni: List<ElementoRisposta>, questioniAperte: List<ElementoRisposta>, azioni: List<AzioneRisposta>, puntiChiave: List<PuntoChiaveRisposta>) — raw, UNVERIFIED; speakers only as {V<n>}
  - `ElementoRisposta`: data class(testo: String, fonti: List<Int>)
  - `AzioneRisposta`: data class(testo: String, fonti: List<Int>, responsabile: Int?)
  - `PuntoChiaveRisposta`: data class(testo: String, fonti: List<Int>, parlante: Int?)
  - `ErroreApplicazioneSintesi`: sealed : ErroreDominio { ModelloNonDisponibile; IngressoTroppoLungo(token: Int); ErroreRuntime(motivo: String); RispostaNonValida; Annullato } → motivo: modello_non_disponibile, troppo_lunga, errore_modello, errore_modello, (nothing written)
  - key `fonti / responsabile / parlante`: segmentoId / voceId NUMBERS of the Trascritto generation the input was built from (Published Language integers); validity is NOT the port's promise — the root checks it (INV-S4)
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §4, ADR 0023 §5, ADR 0012 (b); related_adrs 0002, 0012, 0021, 0023; tactical-model: features/sintesi/tactical-model.md
