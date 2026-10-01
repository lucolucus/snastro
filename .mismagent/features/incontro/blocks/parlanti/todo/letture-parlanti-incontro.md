---
id: letture-parlanti-incontro
type: read-model
context: parlanti
side: app
wave: 5
release: I1
model_hint: deep
module: ":parlanti:applicazione ..letture (Proposta, PropostaUnione, PianoRiassegnazione, EstrattoAudio, IdentificazioneIncontri, ParlantiDelProgetto)"
consumes:
  - kernel-incontro
  - agg-impronte-per-parte
  - voci-per-parlanti
  - eventi-trascrizione-incontro
  - parti-per-parlanti
related_adrs:
  - "0009"
  - "0012"
  - "0017"
  - "0019"
  - "0035"
view_shape: {"PropostaView": "unchanged shape, per VoceRef(incontroId, voceId); candidati[].estratto: EstrattoRef (registrazioneId of the print's Parte)", "PropostaUnioneView": "[{ voceA, voceB, parlanteId, nome }] per incontroId", "EstrattoRef": "unchanged (registrazioneId, inizioMs, fineMs) — ONE Parte", "IdentificazioneIncontri": "Map<IncontroId, { numVoci, numVociDaIdentificare }>", "ParlantiDelProgetto": "existing + numIncontri (replaces numRegistrazioni)"}
tests_nl_status: draft
---
# letture-parlanti-incontro

## What to do
Re-scope the Parlanti read-models to the Incontro: Proposta with a Galleria including the other Parti's prints and the best Fascia over (slice, print) pairs; Proposta di unione per Incontro; PianoRiassegnazione over the Incontro; EstrattoAudio from one Parte; identificazione per Incontro; ParlantiDelProgetto counts Incontri.

## Tasks
- INV-20 Proposta for Voce 5 (Parte 2) ranks Anna (print from Parte 1 of the same Incontro) as a Candidato; its Fascia is the best over Voce 5's slices × Anna's prints; no numeric score is exposed
- INV-22 two Voci of the same Incontro (Parti 1 and 2) attributed to Marco → one PropostaUnioneView entry; never for Voci of two different Incontri
- INV-27 PianoRiassegnazione over a 2-Parte Incontro: the reference Parlante's target Voce is its lowest voceId in the Incontro; segments of both Parti are candidates
- INV-I17 EstrattoAudio of a Voce speaking 40 s in Parte 1 and 90 s in Parte 2 comes from Parte 2; on a tie, from Parte 1; a Candidato's extract comes from the Parte of its chosen print
- AC-I47 IdentificazioneIncontri counts the Incontro's Voci and the unattributed ones; ParlantiDelProgetto.numIncontri counts distinct Incontri

## Dependencies
- `agg-impronte-per-parte` (consumes it; owner `parlante-impronte-per-parte`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: Impronta, sorgente: String, modello: String): Esito<Unit> /* replaces the one for (voce, parte), adds otherwise; refused when eliminato */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce: VoceRef, parte: RegistrazioneId); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules, also inheritance */; impronte: List<ImprontaVocale> /* a copy */ — AMENDED 2026-10-02 (user, D-0034) to the realized shape; transitional delegates registraImpronta / trasferisciImpronta / rimuoviImpronta(voce) kept until attribuzione-incontro and politiche-parlanti-incontro move to these names and delete them
  - pinned `ImprontaVocale`: (voceRef: VoceRef, impronta: Impronta, sorgente: String /* SorgenteImpronta.chiave, ADR 0012 (b) */, modello: String, parte: RegistrazioneId) — the existing type, gained parte in incontro-chiavi (replaces the earlier ImprontaDiParte pin)
  - key `(parlanteId, voceRef, parte)`: unique per print (INV-I8; impronta_vocale UNIQUE)
- `eventi-trascrizione-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `rigenerazione-sbobinatura-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `avvio-incontro`, `avvio-proposta-tra-parti` · contract_test: consumer-driven
  - pinned `ElaborazioneCompletata`: (registrazioneId, incontroId) — after commit
  - pinned `TrascrittoSostituito`: (registrazioneId, incontroId, vociRimosse: Set<VoceId>) — in the completion unit, BEFORE ElaborazioneCompletata; synchronous consumer Parlanti only (Sintesi never)
  - pinned `TrascrittoEliminato`: (registrazioneId, incontroId, vociRimosse: Set<VoceId>) — NEW; published inside the deleting unit by the Trascrizione elimination policy iff the Parte had a Trascritto; synchronous consumer Parlanti (nested, depth-first)
  - pinned `VociUnite`: (incontroId, sopravvissuta: VoceId, rimossa: VoceId)
  - pinned `VoceDivisa`: (incontroId, origine: VoceId, nuova: VoceId, spostati: List<SegmentoRef>)
  - pinned `SegmentoRiassegnato`: (incontroId, segmento: SegmentoRef, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean)
  - pinned `SegmentoConfermato`: (incontroId, segmento: SegmentoRef, confermato: Boolean)
  - pinned `ElaborazioneAvviata / Fallita / Annullata`: unchanged (registrazioneId)
  - key `vociRimosse`: minted by voci-dell-incontro (completaParte / rimuoviParte) — the Voci that ceased in that unit
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `parti-per-parlanti` (consumes it; owner `incontro-chiavi`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Parlanti).parti`: (incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. VoceRef → its Parti: parti(incontroId), then the existing per-Parte LettoreVoci.voci(r) (VoceRefs carry the incontroId), keep the Parti where that voceId speaks; audio decoded per Parte with DecodificatoreAudio.campioni(r, …); no new Trascrizione method. Widened by porte-parlanti-incontro to List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>?, ordered
  - key `incontroId`: as kernel-incontro
- `viste-parlanti-incontro` (owns it) — consumers: `schermata-incontri`, `pannello-voci-incontro`, `schermata-parlanti-incontri`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `PropostaView`: unchanged shape, keyed by VoceRef(incontroId, voceId)
  - pinned `PropostaUnioneView`: List<(voceA: VoceId, voceB: VoceId, parlanteId, nome)> per incontroId
  - pinned `IdentificazioneIncontri`: Map<IncontroId, (numVoci: Int, numVociDaIdentificare: Int)>
  - pinned `ParlantiDelProgetto (amended)`: numIncontri replaces numRegistrazioni
  - key `voceRef`: as kernel-incontro
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: parti(incontroId) WIDENED from List<RegistrazioneId>? (boundary parti-per-parlanti) to List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate), ordered by INV-I2; registrazione(id).incontroId as boundary registrazione-incontro-id
  - key `voceRef`: as kernel-incontro

Sources: ADR 0035 §6 · tactical-model.md [INV-20], [INV-22], [INV-27], [INV-I17] · ADR 0019 amendment 2026-10-01
