---
id: schermata-parlanti-incontri
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.parlanti"
consumes:
  - kernel-incontro
  - viste-parlanti-incontro
consumes_rm:
  - letture-parlanti-incontro
triggers: []
tests_nl_status: draft
---
# schermata-parlanti-incontri

## What to do
S4 count text: 'compare in N incontri' instead of 'compare in N registrazioni'.

## Tasks
- AC-I82 a Parlante appearing in 3 Parti of one Incontro and in another Incontro shows 'compare in 2 incontri'; empty state and errors unchanged from today

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `viste-parlanti-incontro` (consumes it; owner `letture-parlanti-incontro`) — consumers: `schermata-incontri`, `pannello-voci-incontro`, `schermata-parlanti-incontri`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `PropostaView`: unchanged shape, keyed by VoceRef(incontroId, voceId)
  - pinned `PropostaUnioneView`: List<(voceA: VoceId, voceB: VoceId, parlanteId, nome)> per incontroId
  - pinned `IdentificazioneIncontri`: Map<IncontroId, (numVoci: Int, numVociDaIdentificare: Int)>
  - pinned `ParlantiDelProgetto (amended)`: numIncontri replaces numRegistrazioni
  - key `voceRef`: as kernel-incontro

Sources: UI/ux-proposal.md § S4
