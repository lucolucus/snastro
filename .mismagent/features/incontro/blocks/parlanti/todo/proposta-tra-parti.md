---
id: proposta-tra-parti
type: read-model
context: parlanti
side: app
wave: 5
release: I3
high_value: true
module: ":parlanti:applicazione ..letture (PropostaTraParti)"
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
  - "0036"
view_shape: {"PropostaTraParti": "[{ voceA: VoceId, parteA: Int, estrattoA: EstrattoRef, voceB: VoceId, parteB: Int, estrattoB: EstrattoRef }]"}
tests_nl_status: confirmed
---
# proposta-tra-parti

## What to do
New read-model PropostaTraParti per incontroId: pairs of unattributed Voci of different Parti that are each other's only FORTE (existing SoglieFascia), computed in memory from transient prints, never written, never automatic.

## Tasks
- INV-I18 table test on fake prints: mutual single FORTE between unattributed Voci of Parti 1 and 2 → one pair; A has two FORTE → nothing for A; B is A's FORTE but A is not B's only FORTE → nothing; a FORTE already in another pair → nothing; only DEBOLE → nothing; one Voce attributed → nothing; Voci sharing a Parte → nothing
- AC-I48 the similarity is the best cosine over the two Voci's (Voce, Parte) slices compared with the same SoglieFascia as the Proposta (0.7 / 0.5); exactly one extraction per eligible slice per computation (counting fake extractor)
- AC-I49 nothing is written: the fake repositories are untouched and no embedding is persisted; the cache per incontroId is invalidated by VociUnite, ElaborazioneCompletata, TrascrittoEliminato, AttribuzioneConfermata; a cancelled computation leaves no cache entry

## Dependencies
- `agg-impronte-per-parte` (consumes it; owner `parlante-impronte-per-parte`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale) /* replaces the one for (voce, parte) */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce, parte); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules */; impronte: List<ImprontaDiParte>
  - pinned `ImprontaDiParte`: (voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale, sorgente: SorgenteImpronta, modello: String)
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
- `vista-proposta-tra-parti` (owns it) — consumers: `banner-proposta-tra-parti`, `avvio-proposta-tra-parti` · contract_test: consumer-driven
  - pinned `PropostaTraParti`: List<CoppiaTraParti(voceA: VoceId, parteA: Int, estrattoA: EstrattoRef, voceB: VoceId, parteB: Int, estrattoB: EstrattoRef)> per incontroId — parteA < parteB
  - key `voceRef`: as kernel-incontro
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: parti(incontroId) WIDENED from List<RegistrazioneId>? (boundary parti-per-parlanti) to List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate), ordered by INV-I2; registrazione(id).incontroId as boundary registrazione-incontro-id
  - key `voceRef`: as kernel-incontro

Sources: ADR 0036 · tactical-model.md [INV-I18] · decisions.md D-0017, D-0021
