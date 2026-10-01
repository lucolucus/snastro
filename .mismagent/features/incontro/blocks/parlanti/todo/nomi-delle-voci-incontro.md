---
id: nomi-delle-voci-incontro
type: read-model
context: parlanti
side: app
wave: 5
release: I1
module: ":parlanti:applicazione ..letture (NomiDelleVoci)"
consumes:
  - kernel-incontro
  - voci-per-parlanti
related_adrs:
  - "0033"
view_shape: {"NomiIncontro": "Map<VoceRef, String>   // attributed Voci of the Incontro only", "IncontriCon": "List<IncontroId>"}
tests_nl_status: draft
---
# nomi-delle-voci-incontro

## What to do
Re-key the Parlanti supplier of names: nomi(incontroId) for attributed Voci only and incontriCon(parlanteId) replacing registrazioniCon.

## Tasks
- AC-I46 nomi(i) holds one entry per attributed Voce of i (whatever the Parti it spans), none for unattributed or removed Voci; incontriCon(Anna) lists each Incontro once where Anna is attributed

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `nomi-incontro` (owns it) — consumers: `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `NomiDelleVoci.nomi`: (incontroId): Map<VoceRef, String> — attributed Voci only
  - pinned `NomiDelleVoci.incontriCon`: (p: ParlanteId): List<IncontroId> — replaces registrazioniCon
  - key `voceRef`: as kernel-incontro
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: registrazione(id) + incontroId; parti(incontroId): List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate)
  - key `voceRef`: as kernel-incontro

Sources: ADR 0033 §4
