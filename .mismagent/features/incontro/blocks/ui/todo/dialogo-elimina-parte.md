---
id: dialogo-elimina-parte
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.registrazioni (Elimina dialog)"
consumes:
  - kernel-incontro
  - vista-incontri
consumes_rm:
  - incontri-del-progetto
triggers:
  - EliminaRegistrazione
tests_nl_status: draft
---
# dialogo-elimina-parte

## What to do
The Elimina dialog for a non-last Parte with ADR 0038 §5's title and texts; the last Parte and 1-part Incontri keep today's dialog; disabled cases per Parte.

## Tasks
- AC-I72 Parte 2 of 3 with a transcript → title 'Eliminare la parte 2 di «titolo»?' and ADR 0038 §5's text including 'Il riassunto dell'incontro resta leggibile ma diventa superato.'; the last Parte → today's dialog unchanged
- AC-I73 'Elimina…' disabled with today's captions when THAT Parte is in coda or in corso, enabled when another Parte is; after Elimina the sub-row disappears and today's notice appears

## Dependencies
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

Sources: ADR 0038 §5 · UI/ux-proposal.md § Elimina
