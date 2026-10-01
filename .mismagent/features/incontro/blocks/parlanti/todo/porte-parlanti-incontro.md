---
id: porte-parlanti-incontro
type: port
context: parlanti
side: app
wave: 4
release: I1
module: ":parlanti:applicazione ..porte (+ testFixtures)"
consumes:
  - kernel-incontro
  - agg-impronte-per-parte
  - parti-per-parlanti
  - registrazione-incontro-id
related_adrs:
  - "0033"
  - "0035"
tests_nl_status: draft
---
# porte-parlanti-incontro

## What to do
Re-shape the Parlanti consumer ports: LettoreVoci.voci(incontroId) with intervals per Parte and segmenti(incontroId) by SegmentoRef; WIDEN LettoreRegistrazione (Parlanti).parti(incontroId) from List<RegistrazioneId>? (unordered, owned by incontro-chiavi, D-0031) to List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>? ordered by INV-I2; each with Contratto and Finta.

## Tasks
- AC-I24 LettoreVociContratto: VoceRefs carry the incontroId; a Voce speaking in two Parti comes once with intervals per Parte; after unire across Parti the survivor holds both Parti's intervals; null when no Parte is transcribed
- AC-I25 LettoreRegistrazioneContratto (Parlanti), widened: parti(incontroId) now ordered and numbered 1..N with dataRegistrazione of each; the first Parte's date is the Incontro's date; the four unordered cases of AC-I205 still hold; null for an unknown Incontro

## Dependencies
- `agg-impronte-per-parte` (consumes it; owner `parlante-impronte-per-parte`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale) /* replaces the one for (voce, parte) */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce, parte); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules */; impronte: List<ImprontaDiParte>
  - pinned `ImprontaDiParte`: (voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale, sorgente: SorgenteImpronta, modello: String)
  - key `(parlanteId, voceRef, parte)`: unique per print (INV-I8; impronta_vocale UNIQUE)
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
- `registrazione-incontro-id` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sintesi-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RegistrazioneVista.incontroId`: IncontroId — on Progetto's public view and on each consumer's own view of registrazione(id) (Trascrizione, Parlanti, Sintesi); final shape, no widening
  - pinned `TrascrittoRepository (transition, wave 2)`: trova(r: RegistrazioneId, incontroId: IncontroId): Trascritto?; rimuovi(r, incontroId); salva(t) writes voci_incontro/voce_incontro with t.incontroId; the Trascritto carries incontroId; replaced by VociDellIncontroRepository.trova(incontroId) in wave 3/4
  - key `incontroId`: as kernel-incontro; Trascrizione services read it via LettoreRegistrazione.registrazione(r).incontroId before calling the repository; no Trascrizione query reads the registrazione table; no UPDATE names incontro_id (D-0028)
- `voci-per-parlanti` (owns it) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: parti(incontroId) WIDENED from List<RegistrazioneId>? (boundary parti-per-parlanti) to List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate), ordered by INV-I2; registrazione(id).incontroId as boundary registrazione-incontro-id
  - key `voceRef`: as kernel-incontro

Sources: ADR 0033 §4, §4.1 · ADR 0035 §6
