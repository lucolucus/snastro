---
id: adattatori-trascrizione-incontro
type: adapter
context: trascrizione
side: app
wave: 6
release: I1
module: ":trascrizione:adattatori (persistenza VociDellIncontroRepositorySql, porte LettoreRegistrazioneDaProgetto, eventi AbbonatoEliminazioneRegistrazione)"
consumes:
  - kernel-incontro
  - agg-voci-dell-incontro
  - repo-voci-incontro
  - parti-per-trascrizione
  - catalogo-incontro
  - eventi-progetto-incontro
  - registrazione-incontro-id
related_adrs:
  - "0029"
  - "0034"
  - "0035"
tests_nl_status: draft
---
# adattatori-trascrizione-incontro

## What to do
Implement VociDellIncontroRepository in SQL (the only writer of voci_incontro, voce_incontro, trascritto, voce, segmento; one LetturaCoerente read; rewrite only changed Parti) replacing TrascrittoRepositorySql; implement LettoreRegistrazione.parti over CatalogoRegistrazioni.incontro; re-wire the elimination subscriber.

## Tasks
- AC-I56 VociDellIncontroRepositoryContratto runs green on the SQL adapter: a 2-Parte root round-trips; prossima_voce and prossimo_segmento never decrease; saving after a change in Parte B rewrites no row of Parte A (statement count on the fake driver or row timestamps)
- AC-I57 LettoreRegistrazioneContratto (Trascrizione) runs green on the adapter seeded through AggiungiRegistrazione / ModificaDataRegistrazione
- AC-I58 a forgotten voce_incontro delete after rimuoviParte fails the COMMIT on the deferred FK (fail closed)

## Dependencies
- `agg-voci-dell-incontro` (consumes it; owner `voci-dell-incontro`) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
  - pinned `VociDellIncontro`: root(incontroId: IncontroId) — unisci, dividi, riassegna, riassegnaInBlocco, confermaSegmento (SegmentoRef / VoceId), completaParte(registrazioneId, segmentiIniziali): Esito<ConclusioneParte>, rimuoviParte(registrazioneId): Esito<Set<VoceId> /* vociRimosse */>; named predicates: haParte(r), voci, partiDi(voceId)
  - pinned `ConclusioneParte`: sealed { PrimaTrascrizione(vociNuove: Set<VoceId>); Sostituzione(vociRimosse: Set<VoceId>, vociNuove: Set<VoceId>) }
  - pinned `Trascritto`: entity per Parte (registrazioneId; segmenti: List<Segmento>; prossimoSegmento: Int) — no repository of its own
  - key `voceId`: as kernel-incontro (counter prossimaVoce, never reused)
  - key `segmentoId`: as kernel-incontro (prossimoSegmento per Parte, never reused)
- `catalogo-incontro` (consumes it; owner `catalogo-incontro`) — consumers: `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.incontro`: (id: IncontroId): IncontroVista? — null for an unknown or ceased Incontro
  - pinned `IncontroVista`: (incontroId, progettoId, parti: List<ParteVista>) — ordered and numbered by OrdineDelleParti
  - pinned `ParteVista`: (registrazioneId, numero: Int, titolo: String, dataRegistrazione: LocalDate, oraDiInizio: LocalTime?, durataMs: Long)
  - pinned `RegistrazioneVista (amended)`: + oraDiInizio: LocalTime? (incontroId: boundary registrazione-incontro-id)
  - key `numero`: minted at read time by OrdineDelleParti — never stored; changes when a date/time edit, an import or an elimination reorders
- `eventi-progetto-incontro` (consumes it; owner `porte-progetto-incontro`) — consumers: `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `eliminazione-parte-trascrizione`, `eliminazione-parte-sintesi`, `rigenerazione-sbobinatura-incontro`, `adattatori-trascrizione-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `avvio-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `RegistrazioneAggiunta`: existing + incontroId: IncontroId — after commit, one per file
  - pinned `DataRegistrazioneModificata`: existing + incontroId: IncontroId — after commit
  - pinned `OraDiInizioModificata`: (registrazioneId: RegistrazioneId, incontroId: IncontroId, precedente: LocalTime?, nuova: LocalTime?) — NEW, after commit
  - pinned `RegistrazioneEliminata`: (registrazioneId, progettoId, titolo, dataRegistrazione, riferimentoAudio, incontroId: IncontroId, incontroCessato: Boolean) — in-transaction, synchronous subscribers Sintesi → Parlanti(none) → Trascrizione (ADR 0030 order unchanged)
  - key `incontroCessato`: minted by elimina-parte inside its transaction: true iff IncontroRepository.partiDi(incontroId) holds no other Parte
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `parti-per-trascrizione` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Trascrizione)`: registrazione(id) view + incontroId; parti(incontroId: IncontroId): List<ParteDiIncontro>? — ordered by INV-I2, null = unknown Incontro
  - pinned `ParteDiIncontro`: (registrazioneId: RegistrazioneId, numero: Int)
  - key `numero`: as catalogo-incontro
- `registrazione-incontro-id` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sintesi-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RegistrazioneVista.incontroId`: IncontroId — on Progetto's public view and on each consumer's own view of registrazione(id) (Trascrizione, Parlanti, Sintesi); final shape, no widening
  - pinned `TrascrittoRepository (transition, wave 2)`: trova(r: RegistrazioneId, incontroId: IncontroId): Trascritto?; rimuovi(r, incontroId); salva(t) writes voci_incontro/voce_incontro with t.incontroId; the Trascritto carries incontroId; replaced by VociDellIncontroRepository.trova(incontroId) in wave 3/4
  - key `incontroId`: as kernel-incontro; Trascrizione services read it via LettoreRegistrazione.registrazione(r).incontroId before calling the repository; no Trascrizione query reads the registrazione table; no UPDATE names incontro_id (D-0028)
- `repo-voci-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `VociDellIncontroRepository`: interface { fun trova(id: IncontroId): VociDellIncontro? /* one LetturaCoerente snapshot */; fun salva(root: VociDellIncontro) /* rewrites only changed Parti */; fun rimuovi(id: IncontroId); fun trascritto(r: RegistrazioneId): Trascritto? }
  - key `incontroId`: as kernel-incontro

Sources: ADR 0035 §1 · ADR 0034 §2 · ADR 0033 §4
