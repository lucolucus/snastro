---
id: catalogo-incontro
type: read-model
context: progetto
side: app
wave: 5
release: I1
module: ":progetto:applicazione ..letture (CatalogoRegistrazioni)"
consumes:
  - kernel-incontro
  - agg-incontro
  - repo-incontro
related_adrs:
  - "0033"
view_shape: {"IncontroVista": "{ incontroId, progettoId, parti: [ParteVista] }   // ordered by OrdineDelleParti", "ParteVista": "{ registrazioneId, numero: Int /* 1..N */, titolo, dataRegistrazione: LocalDate, oraDiInizio: LocalTime?, durataMs: Long }", "RegistrazioneVista": "existing fields + incontroId, oraDiInizio?"}
tests_nl_status: draft
---
# catalogo-incontro

## What to do
Amend the public query API CatalogoRegistrazioni: incontro(id) returns the Incontro with its Parti ordered and numbered by OrdineDelleParti; RegistrazioneVista carries incontroId and oraDiInizio. The supplier every consumer adapter of parti(incontroId) reads.

## Tasks
- INV-I2 incontro(i) with 3 Parti stored out of order returns them ordered and numbered 1..3 by OrdineDelleParti (date, time empty last, aggiuntaAlle, id); after a time edit the numbers change accordingly
- AC-I37 incontro(unknown) → null; registrazione(r) carries incontroId and oraDiInizio (null when empty)

## Dependencies
- `agg-incontro` (consumes it; owner `incontro`) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable
- `catalogo-incontro` (owns it) — consumers: `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.incontro`: (id: IncontroId): IncontroVista? — null for an unknown or ceased Incontro
  - pinned `IncontroVista`: (incontroId, progettoId, parti: List<ParteVista>) — ordered and numbered by OrdineDelleParti
  - pinned `ParteVista`: (registrazioneId, numero: Int, titolo: String, dataRegistrazione: LocalDate, oraDiInizio: LocalTime?, durataMs: Long)
  - pinned `RegistrazioneVista (amended)`: + incontroId: IncontroId, + oraDiInizio: LocalTime?
  - key `numero`: minted at read time by OrdineDelleParti — never stored; changes when a date/time edit, an import or an elimination reorders
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `repo-incontro` (consumes it; owner `porte-progetto-incontro`) — consumers: `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `adattatori-progetto-incontro` · contract_test: consumer-driven
  - pinned `IncontroRepository`: interface { fun trova(id: IncontroId): Incontro?; fun salva(i: Incontro); fun rimuovi(id: IncontroId) /* refused while a Parte exists */; fun partiDi(id: IncontroId): List<RegistrazioneId> }
  - pinned `RegistrazioneRepository (amended)`: maps incontroId and oraDiInizio (nullable)
  - key `incontroId`: as kernel-incontro

Sources: ADR 0033 §1, §4
