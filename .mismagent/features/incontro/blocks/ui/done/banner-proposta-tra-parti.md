---
id: banner-proposta-tra-parti
type: ui
context: ui
side: app
wave: 6
release: I3
module: ":ui snastro.ui.registrazione (Voci panel banner)"
consumes:
  - kernel-incontro
  - vista-proposta-tra-parti
consumes_rm:
  - proposta-tra-parti
triggers:
  - UnisciVoci
tests_nl_status: draft
---
# banner-proposta-tra-parti

## What to do
The second banner kind of the Voci panel: 'Voce 5 e Voce 1 (parte 1) sembrano la stessa persona' with ▶ for each extract and [Unisci] → UnisciVoci (earlier Parte survives); shown after the Proposta di unione banner, at most one banner per screen; not shown while S3 is read-only.

## Tasks
- AC-I83 one pair → the banner with two ▶ (each plays its EstrattoRef) and [Unisci] sending UnisciVoci with the Voce of the earlier Parte as sopravvissuta; no 'No' button
- AC-I84 a Proposta di unione present → only that banner; S3 read-only → no cross-Parte banner and no computation requested; the pair disappears on the refresh once joined

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `vista-proposta-tra-parti` (consumes it; owner `proposta-tra-parti`) — consumers: `banner-proposta-tra-parti`, `avvio-proposta-tra-parti` · contract_test: consumer-driven
  - pinned `PropostaTraParti`: List<CoppiaTraParti(voceA: VoceId, parteA: Int, estrattoA: EstrattoRef, voceB: VoceId, parteB: Int, estrattoB: EstrattoRef)> per incontroId — voceA = the Voce whose first Parte is earlier (it survives Unisci); parteA/parteB = the Parte its estratto plays from (the Parte where that Voce speaks most), so parteA > parteB is possible
  - key `voceRef`: as kernel-incontro

Sources: UI/ux-proposal.md § S3 cross-Parte proposal · ADR 0036 §1, §3
