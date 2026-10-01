---
id: voci-del-trascritto-incontro
type: read-model
context: trascrizione
side: app
wave: 5
release: I1
module: ":trascrizione:applicazione ..letture (VociDelTrascritto, StatiElaborazione)"
consumes:
  - kernel-incontro
  - agg-voci-dell-incontro
  - repo-voci-incontro
related_adrs:
  - "0029"
  - "0033"
  - "0035"
view_shape: {"VoceIncontroVista": "{ voceRef: VoceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>> }", "SegmentoDiVoceIncontro": "{ segmento: SegmentoRef, voceId, intervallo: IntervalloMs, confermato: Boolean }", "TrascrittoTesto": "existing + incontroId", "StatoParte": "DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA"}
tests_nl_status: draft
---
# voci-del-trascritto-incontro

## What to do
Re-shape the Trascrizione supplier views: voci(incontroId) with intervals per Parte, segmenti(incontroId) by SegmentoRef, trascritto(r) with incontroId, partiConTrascritto(incontroId), segmenti(r) unchanged, and statoParte(r) over StatiElaborazione.

## Tasks
- AC-I39 voci(i) on a 2-Parte root: a Voce spanning both Parti comes once with intervals keyed by each registrazioneId; segmenti(i) lists every Segmento as SegmentoRef with its Incontro voceId; null when no Parte is transcribed
- AC-I40 partiConTrascritto(i) lists only transcribed Parti in Parte order; trascritto(r) carries incontroId
- AC-I41 statoParte table: no Trascritto + no run → DA_TRASCRIVERE; a re-run open on a transcribed Parte → IN_TRASCRIZIONE; no Trascritto + latest run fallita → NON_RIUSCITA; Trascritto + nothing open → TRASCRITTA
- AC-I42 the reads run in one LetturaCoerente snapshot (ADR 0029): a completion committed between two reads of the same call is not half-seen (consistency test with the contract's Ambiente)

## Dependencies
- `agg-voci-dell-incontro` (consumes it; owner `voci-dell-incontro`) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
  - pinned `VociDellIncontro`: root(incontroId: IncontroId) — unisci, dividi, riassegna, riassegnaInBlocco, confermaSegmento (SegmentoRef / VoceId), completaParte(registrazioneId, segmentiIniziali): Esito<ConclusioneParte>, rimuoviParte(registrazioneId): Esito<Set<VoceId> /* vociRimosse */>; named predicates: haParte(r), voci, partiDi(voceId)
  - pinned `ConclusioneParte`: sealed { PrimaTrascrizione(vociNuove: Set<VoceId>); Sostituzione(vociRimosse: Set<VoceId>, vociNuove: Set<VoceId>) }
  - pinned `Trascritto`: entity per Parte (registrazioneId; segmenti: List<Segmento>; prossimoSegmento: Int) — no repository of its own
  - key `voceId`: as kernel-incontro (counter prossimaVoce, never reused)
  - key `segmentoId`: as kernel-incontro (prossimoSegmento per Parte, never reused)
- `api-voci-incontro` (owns it) — consumers: `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `VociDelTrascritto.voci`: (incontroId): List<VoceIncontroVista>? — VoceIncontroVista(voceRef: VoceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); null = no transcribed Parte
  - pinned `VociDelTrascritto.segmenti(incontroId)`: List<SegmentoDiVoceIncontro>? — (segmento: SegmentoRef, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean)
  - pinned `VociDelTrascritto.trascritto`: (r: RegistrazioneId): TrascrittoTesto? — existing + incontroId
  - pinned `VociDelTrascritto.partiConTrascritto`: (incontroId): List<RegistrazioneId> — in Parte order
  - pinned `VociDelTrascritto.segmenti(r)`: unchanged (Sintesi input)
  - pinned `StatiElaborazione.statoParte`: (r: RegistrazioneId): StatoParte — DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA
  - key `voceRef`: as kernel-incontro
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `repo-voci-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `VociDellIncontroRepository`: interface { fun trova(id: IncontroId): VociDellIncontro? /* one LetturaCoerente snapshot */; fun salva(root: VociDellIncontro) /* rewrites only changed Parti */; fun rimuovi(id: IncontroId); fun trascritto(r: RegistrazioneId): Trascritto? }
  - key `incontroId`: as kernel-incontro

Sources: ADR 0033 §4 · ADR 0035 §1
