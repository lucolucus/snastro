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
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale) /* replaces the one for (voce, parte) */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce, parte); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules */; impronte: List<ImprontaDiParte>
  - pinned `ImprontaDiParte`: (voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale, sorgente: SorgenteImpronta, modello: String)
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
  - pinned `RegistrazioneVista (amended)`: + incontroId: IncontroId, + oraDiInizio: LocalTime?
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
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: registrazione(id) + incontroId; parti(incontroId): List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate)
  - key `voceRef`: as kernel-incontro

Sources: ADR 0035 §6 · ADR 0038 §2 · ADR 0034 §2
