---
id: incontri-del-progetto
type: read-model
context: progetto
side: app
wave: 5
release: I2
module: ":progetto:applicazione ..letture (IncontriDelProgetto, replaces RegistrazioniDelProgetto for S2)"
consumes:
  - kernel-incontro
  - agg-incontro
  - repo-incontro
related_adrs:
  - "0033"
view_shape: {"IncontriDelProgetto": "[IncontroDelProgettoVista]   // newest first by data", "IncontroDelProgettoVista": "{ incontroId, titolo /* first Parte's */, data /* first Parte's DataRegistrazione */, durataMs /* sum */, numParti, parti: [ParteVista] }"}
tests_nl_status: draft
---
# incontri-del-progetto

## What to do
The S2 list per owning context (ADR 0033 §5): Incontri of the Progetto newest first by their date, each with derived titolo, data, durata, numParti and its Parti ordered and numbered.

## Tasks
- INV-I3 a 1-part Incontro row carries exactly today's RegistrazioniDelProgetto data (titolo, data, durata) and numParti = 1
- AC-I38 a 3-part Incontro: titolo and data of Parte 1, durataMs the sum, parti ordered and numbered; Incontri sorted newest first by data; an empty Progetto → []

## Dependencies
- `agg-incontro` (consumes it; owner `incontro`) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable
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
- `vista-incontri` (owns it) — consumers: `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `IncontriDelProgetto`: List<IncontroDelProgettoVista> — newest first by data
  - pinned `IncontroDelProgettoVista`: (incontroId, titolo, data: LocalDate, durataMs: Long, numParti: Int, parti: List<ParteVista>)
  - key `incontroId`: as kernel-incontro

Sources: ADR 0033 §5 · UI/ux-proposal.md § S2, § Data views
