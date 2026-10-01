---
id: adattatori-parlanti-incontro
type: adapter
context: parlanti
side: app
wave: 6
release: I1
module: ":parlanti:adattatori (persistenza, porte LettoreVociDaTrascrizione + LettoreRegistrazioneDaProgetto, eventi abbonati)"
consumes:
  - kernel-incontro
  - agg-impronte-per-parte
  - voci-per-parlanti
  - api-voci-incontro
  - catalogo-incontro
  - eventi-trascrizione-incontro
  - parti-di-incontro-progetto
  - parti-per-parlanti
  - registrazione-incontro-id
related_adrs:
  - "0030"
  - "0034"
  - "0035"
  - "0038"
tests_nl_status: draft
---
# adattatori-parlanti-incontro

## What to do
Map prints per (Parlante, VoceRef, Parte) and Attribuzioni by (incontro_id, voce_id) in SQL; implement LettoreVoci over VociDelTrascritto and LettoreRegistrazione.parti over CatalogoRegistrazioni; subscribe the purge to TrascrittoEliminato (nested, synchronous) and drop the RegistrazioneEliminata subscription.

## Tasks
- AC-I59 LettoreVociContratto and LettoreRegistrazioneContratto (Parlanti) run green on the adapters seeded through the Trascrizione and Progetto commands
- AC-I60 ParlanteRepositorySql round-trips two prints of one Voce in two Parti; a print whose (Voce, Parte) slice is gone fails the COMMIT on the second deferred FK
- AC-I61 Parlanti registers NO subscriber for RegistrazioneEliminata; its purge subscriber receives TrascrittoEliminato inside the deleting unit

## Dependencies
- `agg-impronte-per-parte` (consumes it; owner `parlante-impronte-per-parte`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: Impronta, sorgente: String, modello: String): Esito<Unit> /* replaces the one for (voce, parte), adds otherwise; refused when eliminato */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce: VoceRef, parte: RegistrazioneId); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules, also inheritance */; impronte: List<ImprontaVocale> /* a copy */ — AMENDED 2026-10-02 (user, D-0034) to the realized shape; transitional delegates registraImpronta / trasferisciImpronta / rimuoviImpronta(voce) kept until attribuzione-incontro and politiche-parlanti-incontro move to these names and delete them
  - pinned `ImprontaVocale`: (voceRef: VoceRef, impronta: Impronta, sorgente: String /* SorgenteImpronta.chiave, ADR 0012 (b) */, modello: String, parte: RegistrazioneId) — the existing type, gained parte in incontro-chiavi (replaces the earlier ImprontaDiParte pin)
  - key `(parlanteId, voceRef, parte)`: unique per print (INV-I8; impronta_vocale UNIQUE)
- `api-voci-incontro` (consumes it; owner `voci-del-trascritto-incontro`) — consumers: `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `VociDelTrascritto.voci`: (incontroId): List<VoceIncontroVista>? — VoceIncontroVista(voceRef: VoceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); null = no transcribed Parte
  - pinned `VociDelTrascritto.segmenti(incontroId)`: List<SegmentoDiVoceIncontro>? — (segmento: SegmentoRef, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean)
  - pinned `VociDelTrascritto.trascritto`: (r: RegistrazioneId): TrascrittoTesto? — existing + incontroId
  - pinned `VociDelTrascritto.partiConTrascritto`: (incontroId): List<RegistrazioneId> — in Parte order
  - pinned `VociDelTrascritto.segmenti(r)`: unchanged (Sintesi input)
  - pinned `StatiElaborazione.statoParte`: (r: RegistrazioneId): StatoParte — DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA
  - key `voceRef`: as kernel-incontro
- `catalogo-incontro` (consumes it; owner `catalogo-incontro`) — consumers: `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.incontro`: (id: IncontroId): IncontroVista? — null for an unknown or ceased Incontro
  - pinned `IncontroVista`: (incontroId, progettoId, parti: List<ParteVista>) — ordered and numbered by OrdineDelleParti
  - pinned `ParteVista`: (registrazioneId, numero: Int, titolo: String, dataRegistrazione: LocalDate, oraDiInizio: LocalTime?, durataMs: Long)
  - pinned `RegistrazioneVista (amended)`: + oraDiInizio: LocalTime? (incontroId: boundary registrazione-incontro-id)
  - key `numero`: minted at read time by OrdineDelleParti — never stored; changes when a date/time edit, an import or an elimination reorders
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
- `parti-di-incontro-progetto` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `adattatori-sintesi-incontro`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.parti`: (incontroId: IncontroId): List<RegistrazioneId>? in :progetto:applicazione ..letture — UNORDERED (no consumer sorts or relies on the order); null = unknown Incontro or one whose last Parte was deleted; a known Incontro has ≥ 1 element (INV-I1). Widened by catalogo-incontro into incontro(id): IncontroVista?, parti stays its projection
  - key `incontroId`: as kernel-incontro
- `parti-per-parlanti` (consumes it; owner `incontro-chiavi`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Parlanti).parti`: (incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. VoceRef → its Parti: parti(incontroId), then the existing per-Parte LettoreVoci.voci(r) (VoceRefs carry the incontroId), keep the Parti where that voceId speaks; audio decoded per Parte with DecodificatoreAudio.campioni(r, …); no new Trascrizione method. Widened by porte-parlanti-incontro to List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>?, ordered
  - key `incontroId`: as kernel-incontro
- `registrazione-incontro-id` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sintesi-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RegistrazioneVista.incontroId`: IncontroId — on Progetto's public view and on each consumer's own view of registrazione(id) (Trascrizione, Parlanti, Sintesi); final shape, no widening
  - pinned `TrascrittoRepository (transition, wave 2)`: trova(r: RegistrazioneId, incontroId: IncontroId): Trascritto?; rimuovi(r, incontroId); salva(t) writes voci_incontro/voce_incontro with t.incontroId; the Trascritto carries incontroId; replaced by VociDellIncontroRepository.trova(incontroId) in wave 3/4
  - key `incontroId`: as kernel-incontro; Trascrizione services read it via LettoreRegistrazione.registrazione(r).incontroId before calling the repository; no Trascrizione query reads the registrazione table; no UPDATE names incontro_id (D-0028)
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: parti(incontroId) WIDENED from List<RegistrazioneId>? (boundary parti-per-parlanti) to List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate), ordered by INV-I2; registrazione(id).incontroId as boundary registrazione-incontro-id
  - key `voceRef`: as kernel-incontro

Sources: ADR 0035 §6 · ADR 0038 §2 · ADR 0034 §2
