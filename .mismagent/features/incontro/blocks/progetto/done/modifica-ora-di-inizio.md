---
id: modifica-ora-di-inizio
type: application-service
context: progetto
side: app
wave: 5
release: I2
module: ":progetto:applicazione ..comandi"
consumes:
  - kernel-incontro
  - agg-incontro
  - repo-incontro
  - eventi-progetto-incontro
related_adrs:
  - "0033"
commands:
  - ModificaOraDiInizio
tests_nl_status: draft
---
# modifica-ora-di-inizio

## What to do
New command ModificaOraDiInizio(registrazioneId, ora?) beside ModificaDataRegistrazione: same value → no event; otherwise OraDiInizioModificata after commit.

## Tasks
- ModificaOraDiInizio(r, 10:25:00) on an empty time → stored, OraDiInizioModificata(r, incontroId, null, 10:25:00) published after commit
- ModificaOraDiInizio(r, null) clears a set time and publishes the event; the same value → Ok, no event, nothing written
- ModificaOraDiInizio on an unknown Registrazione → Errore(RegistrazioneNonTrovata); an invalid time is refused by OraDiInizio.di before the command (INV-I14), nothing written

## Dependencies
- `agg-incontro` (consumes it; owner `incontro`) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable
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
- `repo-incontro` (consumes it; owner `porte-progetto-incontro`) — consumers: `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `adattatori-progetto-incontro` · contract_test: consumer-driven
  - pinned `IncontroRepository`: interface { fun trova(id: IncontroId): Incontro?; fun salva(i: Incontro); fun rimuovi(id: IncontroId) /* refused while a Parte exists */; fun partiDi(id: IncontroId): List<RegistrazioneId> }
  - pinned `RegistrazioneRepository (amended)`: maps incontroId and oraDiInizio (nullable)
  - key `incontroId`: as kernel-incontro

Sources: ADR 0033 §1, §3 · decisions.md D-0009
