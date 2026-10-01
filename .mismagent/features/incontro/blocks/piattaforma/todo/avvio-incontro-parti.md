---
id: avvio-incontro-parti
type: adapter
context: piattaforma
side: app
wave: 7
release: I2
module: ":avvio (registrazioni, navigation, smoke fixture)"
consumes:
  - kernel-incontro
  - vista-incontri
  - vista-parte
  - eventi-progetto-incontro
reuses:
  - trascrizione-con-parlanti/tec-shell-ui
related_adrs:
  - "0033"
  - "0039"
tests_nl_status: draft
---
# avvio-incontro-parti

## What to do
Wire the I2 surfaces: S2 on IncontriDelProgetto joined with stati-elaborazione, VociIncontro, identificazione and PosizioniNellaCoda; the import dialog, Aggiungi parti, ModificaOraDiInizio, AvviaElaborazioniDellIncontro; S3 Parte navigation and the chip switch; a multi-part smoke fixture.

## Tasks
- AC-I89 e2e: importing 2 files as 'Un incontro in 2 parti', 'Trascrivi 2 parti' with 4 persone, and the queue completes Parte 1 before Parte 2 (Parte 1's Voci get the lower numbers)
- AC-I90 navigation: deleting the open Parte returns to S2; deleting another Parte reloads the open Parte's page; the Parte switcher and a chip of another Parte open the right page
- AC-I91 smoke on a 2-part fixture captures S2 collapsed and expanded, S3 of each Parte and the Riassunto tab

## Dependencies
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
- `vista-incontri` (consumes it; owner `incontri-del-progetto`) — consumers: `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `IncontriDelProgetto`: List<IncontroDelProgettoVista> — newest first by data
  - pinned `IncontroDelProgettoVista`: (incontroId, titolo, data: LocalDate, durataMs: Long, numParti: Int, parti: List<ParteVista>)
  - key `incontroId`: as kernel-incontro
- `vista-parte` (consumes it; owner `viste-parte-incontro`) — consumers: `schermata-incontri`, `schermata-parte`, `pannello-voci-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `TrascrittoView (amended)`: + incontroId, numeroParte: Int, parti: List<ParteRef(registrazioneId, numero)>, solaLettura: RitrascrizioneInCorso(parte: Int?)?; voci = Voci with Segmenti in this Parte, each + altreParti: List<Int>
  - pinned `VociIncontro`: (voci: List<VoceIncontroRiga(voceId, etichetta, parti: List<Int>)>, numVoci: Int)
  - pinned `numeroPersonePrecompilato`: (incontroId): Int? — latest Elaborazione's numeroPersone over the Incontro
  - key `numeroParte`: as catalogo-incontro (derived at read time)

Sources: ADR 0033 §5 · UI/ux-proposal.md
